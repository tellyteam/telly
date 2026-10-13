package io.github.tellyteam.telly.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A text field that behaves on a TV remote: D-pad focus only highlights it, Select opens the
 * keyboard, and Done or Up/Down leaves it. A plain TextField opens the keyboard as soon as it is
 * focused and swallows Up/Down, which traps remote users inside the form.
 */
@Composable
fun TvTextField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    /** Lets a screen give this field the initial focus. */
    focusRequester: FocusRequester? = null,
    onChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    val rowFocus = focusRequester ?: remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val shown = if (isPassword) "•".repeat(value.length) else value

    if (editing) {
        var hadFocus by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                returnFocus = true
                editing = false
            }),
            modifier = modifier
                .focusRequester(fieldFocus)
                .onFocusChanged {
                    if (hadFocus && !it.isFocused) editing = false
                    hadFocus = it.isFocused
                }
                .onPreviewKeyEvent { event ->
                    val direction = when (event.key) {
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionUp -> FocusDirection.Up
                        else -> null
                    }
                    if (direction != null && event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                    direction != null
                },
        )
        LaunchedEffect(Unit) { fieldFocus.requestFocus() }
    } else {
        Column(
            modifier
                .focusRequester(rowFocus)
                .focusRing()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { editing = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                shown.ifEmpty { placeholder ?: " " },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                color = if (shown.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        LaunchedEffect(returnFocus) {
            if (returnFocus) {
                rowFocus.requestFocus()
                returnFocus = false
            }
        }
    }
}
