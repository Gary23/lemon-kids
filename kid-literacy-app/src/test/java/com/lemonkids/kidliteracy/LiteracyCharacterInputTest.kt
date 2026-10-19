package com.lemonkids.kidliteracy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiteracyCharacterInputTest {
    @Test fun acceptsCharactersAndCommonSeparators() {
        assertEquals("春", parseLiteracyCharacterInput("春").characters)
        assertEquals("春夏秋冬", parseLiteracyCharacterInput(" 春、夏，秋,冬 春\n").characters)
        assertNull(parseLiteracyCharacterInput("春、夏").error)
    }

    @Test fun reportsEmptyInvalidAndTooManyCharactersSeparately() {
        assertEquals("请输入一个或多个汉字", parseLiteracyCharacterInput("  、，\n").error)
        assertEquals("仅支持汉字，可用空格、顿号或逗号分隔", parseLiteracyCharacterInput("春A").error)
        assertEquals("去重后一次最多 12 个字", parseLiteracyCharacterInput("一二三四五六七八九十春夏秋").error)
    }
}
