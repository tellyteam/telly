package io.github.tellyteam.telly.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupInOrderTest {

    @Test
    fun `follows category order, skips empty categories, appends unknown ones`() {
        val items = listOf("b1" to "B", "x1" to "X", "a1" to "A", "b2" to "B")
        val groups = groupInOrder(items, listOf("A", "Empty", "B"), { it.second }) { name, list -> name to list.map { it.first } }
        assertEquals(listOf("A" to listOf("a1"), "B" to listOf("b1", "b2"), "X" to listOf("x1")), groups)
    }
}
