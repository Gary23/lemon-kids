package com.lemonkids.kidtask.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TaskCategoryAppearanceTest {
    @Test
    fun tenVisibleCategoriesUseTenDifferentColors() {
        val keys = (1..10).map { "category-$it" }
        val appearances = assignTaskCategoryAppearances(keys)

        assertEquals(10, appearances.size)
        assertEquals(10, appearances.values.map { it.first }.toSet().size)
    }

    @Test
    fun assignmentDoesNotDependOnTaskOrderOrDuplicateEntries() {
        val keys = listOf("reading", "sport", "homework", "reading")

        assertEquals(assignTaskCategoryAppearances(keys),
            assignTaskCategoryAppearances(keys.reversed()))
    }

    @Test
    fun hashCollisionUsesAnotherAvailableColor() {
        val appearances = assignTaskCategoryAppearances(listOf("Aa", "BB"))

        assertNotEquals(appearances.getValue("Aa").first, appearances.getValue("BB").first)
    }
}
