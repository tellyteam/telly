package io.github.tellyteam.telly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import coil.imageLoader
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.tellyteam.telly.BuildConfig
import io.github.tellyteam.telly.data.update.Release
import io.github.tellyteam.telly.data.update.ReleaseParser
import io.github.tellyteam.telly.container
import io.github.tellyteam.telly.data.Channel
import io.github.tellyteam.telly.data.ChannelGroup
import io.github.tellyteam.telly.data.Details
import io.github.tellyteam.telly.data.NewEpisodes
import io.github.tellyteam.telly.data.SeriesData
import io.github.tellyteam.telly.data.Provider
import io.github.tellyteam.telly.data.Season
import io.github.tellyteam.telly.data.Series
import io.github.tellyteam.telly.data.SeriesGroup
import io.github.tellyteam.telly.data.Source
import kotlin.coroutines.cancellation.CancellationException

sealed interface UiState {
    /** No provider yet, or the user chose to edit it (then [existing] pre-fills the form). */
    data class Setup(val existing: Provider? = null) : UiState
    data class Loading(val provider: Provider) : UiState
    data class Ready(val provider: Provider, val live: List<ChannelGroup>) : UiState
    data class Failed(val provider: Provider, val message: String) : UiState
}

/** A section that loads on demand (films, series, a series' seasons). */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

fun <T, R> Load<T>.map(transform: (T) -> R): Load<R> = when (this) {
    is Load.Done -> Load.Done(transform(value))
    is Load.Failed -> this
    Load.Idle -> Load.Idle
    Load.Loading -> Load.Loading
}

data class SeriesDetail(val series: Series, val data: Load<SeriesData>) {
    val seasons: Load<List<Season>>
        get() = when (data) {
            is Load.Done -> Load.Done(data.value.seasons)
            is Load.Failed -> data
            Load.Idle -> Load.Idle
            Load.Loading -> Load.Loading
        }
}

data class FilmDetail(val film: Channel, val details: Load<Details>)

/** Where the app-update check has got to. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    /** Telly needs "install unknown apps" permission first; the settings screen has been opened. */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Every catalogue is shown from the copy saved last time (instant), then refreshed from the
 * provider in the background. Films and series preload as soon as live TV is up.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val repo = container.channels
    private val _state = MutableStateFlow<UiState>(UiState.Setup())
    val state: StateFlow<UiState> = _state
    private var loadJob: Job? = null

    private val _films = MutableStateFlow<Load<List<ChannelGroup>>>(Load.Idle)
    val films: StateFlow<Load<List<ChannelGroup>>> = _films
    private val _series = MutableStateFlow<Load<List<SeriesGroup>>>(Load.Idle)
    val series: StateFlow<Load<List<SeriesGroup>>> = _series
    private val _seriesDetail = MutableStateFlow<SeriesDetail?>(null)
    val seriesDetail: StateFlow<SeriesDetail?> = _seriesDetail
    private val _filmDetail = MutableStateFlow<FilmDetail?>(null)
    val filmDetail: StateFlow<FilmDetail?> = _filmDetail

    /** Settings open over the browse screens, so closing it needs no reload. */
    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    /** Number of background refreshes running, for the "Updating…" hint. */
    private val _updating = MutableStateFlow(0)
    val updating: StateFlow<Int> = _updating

    /** Large catalogues are downloaded and parsed one at a time to stay within a TV stick's memory. */
    private val heavy = Mutex()

    /** Bumped whenever the provider changes, so late results from the old one are dropped. */
    private var generation = 0

    /** One new-episode check at a time. */
    private val episodeCheck = Mutex()

    init {
        // Weekly, quietly: only says anything if there is a newer version.
        if (container.updates.dueForCheck()) checkForUpdates(manual = false)
        viewModelScope.launch { _series.collect { if (it is Load.Done) checkForNewEpisodes() } }
        val saved = container.providerStore.load()
        if (saved != null) connect(saved, save = false) else devProvider()?.let { _state.value = UiState.Setup(it) }
    }

    fun openSettings() {
        _settingsOpen.value = true
    }

    fun closeSettings() {
        _settingsOpen.value = false
    }

    fun connect(provider: Provider, save: Boolean = true) {
        _settingsOpen.value = false
        container.networkPolicy.allowCleartext = provider.allowCleartext
        resetCatalogues()
        val gen = generation
        loadJob?.cancel()
        _state.value = UiState.Loading(provider)
        loadJob = viewModelScope.launch {
            val cached = if (save) null else runCatching { repo.liveGroups(provider, Source.CACHE) }.getOrNull()
            if (cached != null) _state.value = UiState.Ready(provider, cached)
            refreshing {
                try {
                    val fresh = repo.liveGroups(provider, Source.NETWORK)!!
                    // Only remember a provider once it has actually worked.
                    if (save) container.providerStore.save(provider)
                    if (gen == generation) _state.value = UiState.Ready(provider, fresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // With a saved list on screen, a failed refresh just keeps it.
                    if (cached == null) _state.value = UiState.Failed(provider, e.message ?: e.javaClass.simpleName)
                }
            }
            if (_state.value is UiState.Ready) {
                loadFilms()
                loadSeries()
            }
        }
    }

    /** Re-downloads everything now; the saved lists stay on screen until the new ones arrive. */
    fun refreshEverything() {
        val provider = (_state.value as? UiState.Ready)?.provider ?: return
        connect(provider, save = false)
    }

    /** Deletes saved catalogues and cached artwork, then downloads afresh. Favourites, progress and history are kept. */
    fun clearSavedData() {
        val provider = (_state.value as? UiState.Ready)?.provider ?: return
        viewModelScope.launch {
            container.channels.clearSavedCatalogues()
            clearImageCaches()
            connect(provider, save = false)
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private suspend fun clearImageCaches() {
        val loader = getApplication<Application>().imageLoader
        loader.memoryCache?.clear()
        withContext(Dispatchers.IO) { loader.diskCache?.clear() }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        if (manual) _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = try {
                val latest = container.updates.latest()
                when {
                    latest != null && ReleaseParser.isNewer(latest.version, BuildConfig.VERSION_NAME) -> {
                        if (!manual) {
                            Toast.makeText(getApplication(), "telly ${latest.version} is available. Go to Settings to update.", Toast.LENGTH_LONG).show()
                        }
                        UpdateState.Available(latest)
                    }
                    manual -> UpdateState.UpToDate
                    else -> UpdateState.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) UpdateState.Failed("Couldn't check for updates. Try again later.") else UpdateState.Idle
            }
        }
    }

    /** Downloads the release and hands it to Android's installer, which asks the user to confirm. */
    fun installUpdate() {
        val release = when (val u = _update.value) {
            is UpdateState.Available -> u.release
            is UpdateState.NeedsPermission -> u.release
            is UpdateState.Failed -> u.release
            else -> null
        } ?: return
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _update.value = UpdateState.NeedsPermission(release)
            return
        }
        _update.value = UpdateState.Downloading(release, 0f)
        viewModelScope.launch {
            try {
                val apk = container.updates.download(release) { p -> _update.value = UpdateState.Downloading(release, p) }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                _update.value = UpdateState.Available(release)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.value = UpdateState.Failed("The download didn't finish. Check your connection and try again.", release)
            }
        }
    }

    fun retry() {
        (_state.value as? UiState.Failed)?.let { connect(it.provider, save = false) }
    }

    fun editProvider(current: Provider?) {
        loadJob?.cancel()
        _state.value = UiState.Setup(current)
    }

    fun forgetProvider() {
        _settingsOpen.value = false
        loadJob?.cancel()
        container.providerStore.clear()
        container.lastWatched.clear()
        container.playlist = emptyList()
        resetCatalogues()
        _state.value = UiState.Setup()
    }

    fun loadFilms(force: Boolean = false) = loadSection(_films, force) { p, source -> repo.filmGroups(p, source) }

    fun loadSeries(force: Boolean = false) = loadSection(_series, force) { p, source -> repo.seriesGroups(p, source) }

    fun openSeries(series: Series) {
        val provider = xtream() ?: return
        _filmDetail.value = null
        _seriesDetail.value = SeriesDetail(series, Load.Loading)
        loadDetail(
            cache = { repo.seriesData(provider, series, Source.CACHE) },
            network = { repo.seriesData(provider, series, Source.NETWORK) },
            // Ignore results if the user has since opened something else or gone back.
            publish = { data -> if (_seriesDetail.value?.series == series) _seriesDetail.value = SeriesDetail(series, data) },
        )
    }

    fun openFilm(film: Channel) {
        val provider = xtream() ?: return
        _seriesDetail.value = null
        _filmDetail.value = FilmDetail(film, Load.Loading)
        loadDetail(
            cache = { repo.filmDetails(provider, film, Source.CACHE) },
            network = { repo.filmDetails(provider, film, Source.NETWORK) },
            publish = { data -> if (_filmDetail.value?.film == film) _filmDetail.value = FilmDetail(film, data) },
        )
    }

    fun closeFilm() {
        _filmDetail.value = null
    }

    /** Shows the saved copy at once (if any), then the provider's latest. */
    private fun <T> loadDetail(cache: suspend () -> T?, network: suspend () -> T?, publish: (Load<T>) -> Unit) {
        val gen = generation
        viewModelScope.launch {
            val cached = runCatching { cache() }.getOrNull()
            if (cached != null && gen == generation) publish(Load.Done(cached))
            refreshing {
                try {
                    val fresh = network()!!
                    if (gen == generation) publish(Load.Done(fresh))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (cached == null && gen == generation) publish(Load.Failed(e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    /**
     * Brings back series that were watched to the end of what they had, once the provider adds more:
     * the series returns to Continue watching at the next episode ("New episodes" when they arrived
     * after the user watched). Runs as the series list loads and when the player closes; each series
     * is only fetched again after the provider has changed it.
     */
    fun checkForNewEpisodes() {
        val provider = xtream() ?: return
        val groups = (_series.value as? Load.Done)?.value ?: return
        viewModelScope.launch {
            if (!episodeCheck.tryLock()) return@launch
            try {
                val history = container.lastWatched.history.value
                val progress = container.watchProgress
                val candidates = history.filter { it.caughtUp(progress) }
                if (candidates.isEmpty()) return@launch
                val all = withContext(Dispatchers.Default) { groups.flatMap { it.series } }
                val byId = all.associateBy { it.id }
                val byName = all.associateBy { it.name }
                val checks = container.newEpisodeChecks
                for (entry in candidates) {
                    val show = entry.seriesId?.let(byId::get) ?: byName[entry.heading] ?: continue
                    if (!NewEpisodes.due(show, checks.get(show.id), System.currentTimeMillis())) continue
                    val data = try {
                        repo.seriesData(provider, show, Source.NETWORK)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    checks.mark(show.id, show.added)
                    val (season, index) = NewEpisodes.after(data.seasons, entry.current.id) ?: continue
                    val fresh = NewEpisodes.isNew(show, entry.savedAt)
                    container.lastWatched.save(
                        entry.heading, live = false, season.episodes, index,
                        seriesId = show.id, newEpisodes = fresh, toFront = fresh,
                    )
                    if (fresh) Toast.makeText(getApplication(), "New episodes of ${entry.heading}", Toast.LENGTH_LONG).show()
                }
            } finally {
                episodeCheck.unlock()
            }
        }
    }

    fun closeSeries() {
        _seriesDetail.value = null
    }

    private fun <T> loadSection(
        target: MutableStateFlow<Load<T>>,
        force: Boolean,
        block: suspend (Provider.Xtream, Source) -> T?,
    ) {
        val provider = xtream() ?: return
        if (!force && (target.value is Load.Loading || target.value is Load.Done)) return
        val gen = generation
        target.value = Load.Loading
        viewModelScope.launch {
            heavy.withLock {
                if (gen != generation) return@withLock
                val cached = runCatching { block(provider, Source.CACHE) }.getOrNull()
                if (cached != null) target.value = Load.Done(cached)
                refreshing {
                    val result = try {
                        Load.Done(block(provider, Source.NETWORK)!!)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (cached == null) Load.Failed(e.message ?: e.javaClass.simpleName) else null
                    }
                    if (result != null && gen == generation) target.value = result
                }
            }
        }
    }

    private inline fun refreshing(block: () -> Unit) {
        _updating.value++
        try {
            block()
        } finally {
            _updating.value--
        }
    }

    private fun xtream(): Provider.Xtream? = (_state.value as? UiState.Ready)?.provider as? Provider.Xtream

    private fun resetCatalogues() {
        generation++
        _films.value = Load.Idle
        _series.value = Load.Idle
        _seriesDetail.value = null
        _filmDetail.value = null
    }

    /** Debug builds only: a test provider from provider.local.properties, shown pre-filled but not auto-connected. */
    private fun devProvider(): Provider? {
        if (!BuildConfig.DEBUG) return null
        val name = BuildConfig.DEV_PROVIDER_NAME.ifBlank { "Test provider" }
        val cleartext = BuildConfig.DEV_PROVIDER_ALLOWCLEARTEXT.toBoolean()
        return when (BuildConfig.DEV_PROVIDER_TYPE) {
            "xtream" -> Provider.Xtream(
                name, BuildConfig.DEV_PROVIDER_SERVER, BuildConfig.DEV_PROVIDER_USERNAME, BuildConfig.DEV_PROVIDER_PASSWORD, cleartext,
            ).takeIf { it.server.isNotBlank() }
            "m3u" -> Provider.M3u(name, BuildConfig.DEV_PROVIDER_URL, cleartext).takeIf { it.url.isNotBlank() }
            else -> null
        }
    }
}
