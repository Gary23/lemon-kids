package com.lemonkids.kidtask.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChildNameLabelTest {
    @Test
    fun showsOnlyTheCurrentUserNameAcrossLoadingEditingAndSwitching() {
        assertEquals("姓名加载中", childNameLabel("", hasUser = false))
        assertEquals("未设置姓名", childNameLabel("  ", hasUser = true))
        assertEquals("小明", childNameLabel(" 小明 ", hasUser = true))
        assertEquals("小华", childNameLabel("小华", hasUser = true))
        assertEquals("姓名加载中", childNameLabel("小华", hasUser = false))
        assertEquals("小雨", childNameLabel("小雨", hasUser = true))
    }
}
