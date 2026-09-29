import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { hashPasscode, verifyPasscode } from '../lib/passcode'
import { verifyBiometricUnlock } from '../lib/webauthn'

const RELOCK_MS = 5 * 60 * 1000

export interface LockState {
  passcodeHash: string | null
  lockedItemIds: string[]
  lockedAlbumIds: string[]
  unlocked: boolean
  lastActivityAt: number

  hasPasscode: () => boolean
  setPasscode: (passcode: string) => Promise<void>
  clearPasscode: () => void
  unlockWithPasscode: (passcode: string) => Promise<boolean>
  unlockWithBiometrics: () => Promise<boolean>
  lock: () => void
  touchActivity: () => void
  checkAutoRelock: () => void
  lockItem: (id: string) => void
  unlockItem: (id: string) => void
  lockAlbum: (id: string) => void
  unlockAlbum: (id: string) => void
  isItemLocked: (id: string) => boolean
  isAlbumLocked: (id: string) => boolean
}

export const useLockStore = create<LockState>()(
  persist(
    (set, get) => ({
      passcodeHash: null,
      lockedItemIds: [],
      lockedAlbumIds: [],
      unlocked: false,
      lastActivityAt: Date.now(),

      hasPasscode: () => !!get().passcodeHash,
      setPasscode: async (passcode) => {
        const hash = await hashPasscode(passcode)
        set({ passcodeHash: hash })
      },
      clearPasscode: () => set({ passcodeHash: null, unlocked: false, lockedItemIds: [], lockedAlbumIds: [] }),
      unlockWithPasscode: async (passcode) => {
        const hash = get().passcodeHash
        if (!hash) return false
        const ok = await verifyPasscode(passcode, hash)
        if (ok) set({ unlocked: true, lastActivityAt: Date.now() })
        return ok
      },
      unlockWithBiometrics: async () => {
        const ok = await verifyBiometricUnlock()
        if (ok) set({ unlocked: true, lastActivityAt: Date.now() })
        return ok
      },
      lock: () => set({ unlocked: false }),
      touchActivity: () => set({ lastActivityAt: Date.now() }),
      checkAutoRelock: () => {
        const { unlocked, lastActivityAt } = get()
        if (unlocked && Date.now() - lastActivityAt > RELOCK_MS) {
          set({ unlocked: false })
        }
      },
      lockItem: (id) => set((s) => ({ lockedItemIds: s.lockedItemIds.includes(id) ? s.lockedItemIds : [...s.lockedItemIds, id] })),
      unlockItem: (id) => set((s) => ({ lockedItemIds: s.lockedItemIds.filter((x) => x !== id) })),
      lockAlbum: (id) => set((s) => ({ lockedAlbumIds: s.lockedAlbumIds.includes(id) ? s.lockedAlbumIds : [...s.lockedAlbumIds, id] })),
      unlockAlbum: (id) => set((s) => ({ lockedAlbumIds: s.lockedAlbumIds.filter((x) => x !== id) })),
      isItemLocked: (id) => get().lockedItemIds.includes(id),
      isAlbumLocked: (id) => get().lockedAlbumIds.includes(id),
    }),
    {
      name: 'prism.lock',
      partialize: (state) => ({
        passcodeHash: state.passcodeHash,
        lockedItemIds: state.lockedItemIds,
        lockedAlbumIds: state.lockedAlbumIds,
      }),
    },
  ),
)
