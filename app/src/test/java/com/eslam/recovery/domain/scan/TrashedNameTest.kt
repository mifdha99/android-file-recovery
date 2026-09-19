package com.eslam.recovery.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashedNameTest {
    @Test fun trashed() {
        val t = TrashedName.parse(".trashed-1893456000-IMG_2024-01-02.jpg")!!
        assertTrue(t.trashed)
        assertEquals(1893456000L, t.expiresAtSeconds)
        assertEquals("IMG_2024-01-02.jpg", t.originalName)
    }

    @Test fun pending() {
        val t = TrashedName.parse(".pending-1700000000-video.mp4")!!
        assertFalse(t.trashed)
        assertEquals("video.mp4", t.originalName)
    }

    @Test fun notTrashed() {
        assertNull(TrashedName.parse("IMG.jpg"))
        assertNull(TrashedName.parse(".trashed-abc-IMG.jpg"))
        assertNull(TrashedName.parse(".trashed-123-"))
        assertNull(TrashedName.parse(".nomedia"))
    }
}
