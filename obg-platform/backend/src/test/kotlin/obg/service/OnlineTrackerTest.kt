package com.obg.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OnlineTrackerTest {

    @Test
    fun `tracker correctly updates status and handles offline defaults`() {
        val tracker = OnlineTracker()

        // Unseen user should be OFFLINE
        assertFalse(tracker.isOnline("U1"))
        assertEquals(OnlineTracker.Status.OFFLINE, tracker.getStatus("U1"))

        // After seeing a user, they should be IDLE by default or specified status
        tracker.seen("U1")
        assertTrue(tracker.isOnline("U1"))
        assertEquals(OnlineTracker.Status.IDLE, tracker.getStatus("U1"))

        tracker.seen("U2", OnlineTracker.Status.IN_GAME)
        assertTrue(tracker.isOnline("U2"))
        assertEquals(OnlineTracker.Status.IN_GAME, tracker.getStatus("U2"))
    }

    @Test
    fun `disconnect handles gracefully and does not throw`() {
        val tracker = OnlineTracker()
        assertDoesNotThrow { tracker.disconnect("U1") }
    }
}
