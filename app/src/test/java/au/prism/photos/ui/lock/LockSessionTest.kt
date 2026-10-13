package au.prism.photos.ui.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LockSessionTest {
    @Before
    fun reset() {
        LockSession.onAppForegrounded(0)
        LockSession.lock()
    }

    @Test
    fun `relocks after more than the timeout in the background`() {
        LockSession.unlock()
        LockSession.onAppBackgrounded(1_000)
        LockSession.onAppForegrounded(1_000 + LockSession.RELOCK_AFTER_MS + 1)
        assertFalse(LockSession.unlocked.value)
    }

    @Test
    fun `stays unlocked after a short trip to the background`() {
        LockSession.unlock()
        LockSession.onAppBackgrounded(1_000)
        LockSession.onAppForegrounded(1_000 + LockSession.RELOCK_AFTER_MS - 1)
        assertTrue(LockSession.unlocked.value)
    }

    @Test
    fun `foreground without a recorded background does not relock`() {
        LockSession.unlock()
        LockSession.onAppForegrounded(10 * LockSession.RELOCK_AFTER_MS)
        assertTrue(LockSession.unlocked.value)
    }

    @Test
    fun `a second foreground does not reuse the old background time`() {
        LockSession.unlock()
        LockSession.onAppBackgrounded(1_000)
        LockSession.onAppForegrounded(2_000)
        LockSession.onAppForegrounded(1_000 + 10 * LockSession.RELOCK_AFTER_MS)
        assertTrue(LockSession.unlocked.value)
    }

    @Test
    fun `shouldRelock boundaries`() {
        assertFalse(LockSession.shouldRelock(0, Long.MAX_VALUE))
        assertFalse(LockSession.shouldRelock(5, 5 + LockSession.RELOCK_AFTER_MS))
        assertTrue(LockSession.shouldRelock(5, 6 + LockSession.RELOCK_AFTER_MS))
    }
}
