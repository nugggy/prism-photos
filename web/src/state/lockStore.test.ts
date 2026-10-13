import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ACTIVITY_THROTTLE_MS, useLockStore } from './lockStore'
import { createPasscodeRecord, legacyHashPasscode } from '../lib/passcode'
import { resetLockStore } from '../test/fixtures'

describe('lock store', () => {
  beforeEach(() => {
    localStorage.clear()
    resetLockStore()
  })

  it('upgrades a legacy SHA-256 passcode to PBKDF2 on the next successful unlock', async () => {
    useLockStore.setState({ passcode: await legacyHashPasscode('2580') })
    expect((await useLockStore.getState().unlockWithPasscode('2580')).ok).toBe(true)
    const stored = useLockStore.getState().passcode
    expect(typeof stored).toBe('object')
    expect(stored).toMatchObject({ v: 2, algo: 'PBKDF2-SHA256' })
    // The upgraded record still unlocks.
    useLockStore.getState().lock()
    expect((await useLockStore.getState().unlockWithPasscode('2580')).ok).toBe(true)
  })

  it('only lets lock lists shrink, or the passcode be removed, while unlocked', async () => {
    useLockStore.setState({ passcode: await createPasscodeRecord('1234', 1000), lockedItemIds: ['a'], lockedAlbumIds: ['x'] })
    const s = useLockStore.getState()
    expect(s.unlockItems(['a'])).toBe(false)
    expect(s.unlockAlbum('x')).toBe(false)
    expect(s.clearPasscode()).toBe(false)
    expect(useLockStore.getState().lockedItemIds).toEqual(['a'])
    await s.unlockWithPasscode('1234')
    expect(useLockStore.getState().unlockItems(['a'])).toBe(true)
    expect(useLockStore.getState().lockedItemIds).toEqual([])
  })

  it('refuses to lock items before a passcode exists', () => {
    expect(useLockStore.getState().lockItems(['a'])).toBe(false)
    expect(useLockStore.getState().lockedItemIds).toEqual([])
  })

  it('throttles activity updates so key presses do not rewrite storage each time', () => {
    vi.useFakeTimers()
    try {
      vi.setSystemTime(1_000_000)
      useLockStore.setState({ unlocked: true, lastActivityAt: 1_000_000 })
      const setItem = vi.spyOn(Storage.prototype, 'setItem')
      vi.setSystemTime(1_000_000 + 1000)
      useLockStore.getState().touchActivity()
      expect(useLockStore.getState().lastActivityAt).toBe(1_000_000)
      expect(setItem).not.toHaveBeenCalled()
      vi.setSystemTime(1_000_000 + ACTIVITY_THROTTLE_MS + 1)
      useLockStore.getState().touchActivity()
      expect(useLockStore.getState().lastActivityAt).toBe(1_000_000 + ACTIVITY_THROTTLE_MS + 1)
      setItem.mockRestore()
    } finally {
      vi.useRealTimers()
    }
  })
})
