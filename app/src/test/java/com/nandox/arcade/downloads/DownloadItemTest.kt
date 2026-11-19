package com.nandox.arcade.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadItemTest {
    @Test
    fun percentIsComputedFromBytes() {
        assertEquals(50, DownloadItem(1, "a", DownloadState.RUNNING, 50, 100).percent)
    }

    @Test
    fun unknownTotalHasNoPercent() {
        assertNull(DownloadItem(1, "a", DownloadState.RUNNING, 50, -1).percent)
    }
}
