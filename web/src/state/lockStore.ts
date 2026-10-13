import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import {
  createPasscodeRecord,
  getLockoutRemainingMs,
  recordFailedAttempt,
  resetFailedAttempts,
  verifyPasscode,
  type StoredPasscode,
} from '../lib/passcode'
import { verifyBiometricUnlock } from '../lib/webauthn'

const RELOCK_MS = 5 * 60 * 1000
/** Activity is recorded at most this often, so typing does not rewrite localStorage on every key. */
export const ACTIVITY_THROTTLE_MS = 15_000

export interface UnlockResult {
  ok: boolean
  /** When not ok: how long until another attempt is allowed (0 if allowed now). */
  retryAfterMs: number
}

export interface LockState {
  /** PBKDF2 record, or a legacy SHA-256 hex digest from older builds (upgraded on next unlock). */
  passcode: StoredPasscode | null
  lockedItemIds: string[]
  lockedAlbumIds: string[]
  unlocked: boolean
  lastActivityAt: number

  /** Sets the first passcode, or changes it while unlocked. Returns false if not allowed. */
  setPasscode: (passcode: string) => Promise<boolean>
  /** Removes the passcode and every lock. Only allowed while unlocked. */
  clearPasscode: () => boolean
  unlockWithPasscode: (passcode: string) => Promise<UnlockResult>
  unlockWithBiometrics: () => Promise<boolean>
  lock: () => void
  touchActivity: () => void
  checkAutoRelock: () => void
  /** Hides items. Needs a passcode to exist, otherwise nothing could unlock them. */
  lockItems: (ids: string[]) => boolean
  /** Shows items again. Only allowed while unlocked. */
  unlockItems: (ids: string[]) => boolean
  lockAlbum: (id: string) => boolean
  unlockAlbum: (id: string) => boolean
  /** Wipes all lock data without asking (used on sign out). */
  resetAll: () => void
}

const EMPTY = { passcode: null, lockedItemIds: [] as string[], lockedAlbumIds: [] as string[], unlocked: false }

export const useLockStore = create<LockState>()(
  persist(
    (set, get) => ({
      ...EMPTY,
      lastActivityAt: Date.now(),

      setPasscode: async (passcode) => {
        const { passcode: existing, unlocked } = get()
        if (existing && !unlocked) return false
        const record = await createPasscodeRecord(passcode)
        resetFailedAttempts()
        set({ passcode: record, unlocked: true, lastActivityAt: Date.now() })
        return true
      },
      clearPasscode: () => {
        if (!get().unlocked) return false
        set({ ...EMPTY })
        resetFailedAttempts()
        return true
      },
      unlockWithPasscode: async (passcode) => {
        const stored = get().passcode
        if (!stored) return { ok: false, retryAfterMs: 0 }
        const wait = getLockoutRemainingMs()
        if (wait > 0) return { ok: false, retryAfterMs: wait }
        const result = await verifyPasscode(passcode, stored)
        if (!result.ok) return { ok: false, retryAfterMs: recordFailedAttempt() }
        resetFailedAttempts()
        set({ unlocked: true, lastActivityAt: Date.now(), ...(result.upgraded ? { passcode: result.upgraded } : {}) })
        return { ok: true, retryAfterMs: 0 }
      },
      unlockWithBiometrics: async () => {
        if (!get().passcode) return false
        const ok = await verifyBiometricUnlock()
        if (ok) set({ unlocked: true, lastActivityAt: Date.now() })
        return ok
      },
      lock: () => set({ unlocked: false }),
      touchActivity: () => {
        const now = Date.now()
        const { unlocked, lastActivityAt } = get()
        if (!unlocked || now - lastActivityAt < ACTIVITY_THROTTLE_MS) return
        set({ lastActivityAt: now })
      },
      checkAutoRelock: () => {
        const { unlocked, lastActivityAt } = get()
        if (unlocked && Date.now() - lastActivityAt > RELOCK_MS) {
          set({ unlocked: false })
        }
      },
      lockItems: (ids) => {
        if (!get().passcode) return false
        set((s) => ({ lockedItemIds: Array.from(new Set([...s.lockedItemIds, ...ids])) }))
        return true
      },
      unlockItems: (ids) => {
        if (!get().unlocked) return false
        const remove = new Set(ids)
        set((s) => ({ lockedItemIds: s.lockedItemIds.filter((x) => !remove.has(x)) }))
        return true
      },
      lockAlbum: (id) => {
        if (!get().passcode) return false
        set((s) => ({ lockedAlbumIds: s.lockedAlbumIds.includes(id) ? s.lockedAlbumIds : [...s.lockedAlbumIds, id] }))
        return true
      },
      unlockAlbum: (id) => {
        if (!get().unlocked) return false
        set((s) => ({ lockedAlbumIds: s.lockedAlbumIds.filter((x) => x !== id) }))
        return true
      },
      resetAll: () => {
        set({ ...EMPTY })
        resetFailedAttempts()
      },
    }),
    {
      name: 'prism.lock',
      version: 1,
      partialize: (state) => ({
        passcode: state.passcode,
        lockedItemIds: state.lockedItemIds,
        lockedAlbumIds: state.lockedAlbumIds,
      }),
      migrate: (persisted, version) => {
        const old = (persisted ?? {}) as { passcodeHash?: string | null; passcode?: StoredPasscode | null; lockedItemIds?: string[]; lockedAlbumIds?: string[] }
        // Version 0 stored a bare SHA-256 digest as passcodeHash; it stays usable as a legacy
        // value and is replaced with a PBKDF2 record on the next successful unlock.
        return {
          passcode: (version === 0 ? old.passcodeHash : old.passcode) ?? null,
          lockedItemIds: old.lockedItemIds ?? [],
          lockedAlbumIds: old.lockedAlbumIds ?? [],
        }
      },
    },
  ),
)
