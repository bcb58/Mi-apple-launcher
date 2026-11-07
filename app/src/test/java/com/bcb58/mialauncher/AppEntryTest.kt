package com.bcb58.mialauncher

import com.bcb58.mialauncher.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppEntryTest {

    private val sample = AppEntry(
        packageName = "com.example.player",
        activityName = "com.example.player.MainActivity",
        label = "Example Player",
        isTvApp = true,
        hasBanner = true,
        isSystem = false,
        versionCode = 42L
    )

    @Test
    fun `cache line survives a round trip`() {
        assertEquals(sample, AppEntry.fromLine(sample.toLine()))
    }

    @Test
    fun `labels containing tabs or newlines cannot corrupt the cache file`() {
        val nasty = sample.copy(label = "Bad\tLabel\nSecond")
        val line = nasty.toLine()
        assertEquals(1, line.lines().size)
        val parsed = AppEntry.fromLine(line)!!
        assertEquals(7, line.split('\t').size)
        assertEquals("Bad Label Second", parsed.label)
    }

    @Test
    fun `truncated lines are rejected rather than throwing`() {
        assertNull(AppEntry.fromLine("only\ttwo"))
        assertNull(AppEntry.fromLine(""))
    }

    @Test
    fun `stable ids differ per activity and hold across instances`() {
        val other = sample.copy(activityName = "com.example.player.Second")
        assertNotEquals(sample.stableId, other.stableId)
        assertEquals(sample.stableId, sample.copy(label = "Renamed").stableId)
    }
}
