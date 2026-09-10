package com.dsh.deepseekbalance.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {

    @Test
    fun `small token counts are shown with separators`() {
        assertEquals("0", Fmt.tokens(0))
        assertEquals("1,234", Fmt.tokens(1_234))
        assertEquals("9,999", Fmt.tokens(9_999))
    }

    @Test
    fun `ten thousands are shortened with a chinese unit`() {
        assertEquals("1万", Fmt.tokens(10_000))
        assertEquals("1.23万", Fmt.tokens(12_345))
        assertEquals("9999.9万", Fmt.tokens(99_999_000))
    }

    @Test
    fun `hundreds of millions use the yi unit`() {
        assertEquals("1亿", Fmt.tokens(100_000_000))
        assertEquals("1.5亿", Fmt.tokens(150_000_000))
    }
}
