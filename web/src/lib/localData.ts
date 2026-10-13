import { clearCache } from './cache'
import { clearBiometricUnlock } from './webauthn'
import { useLockStore } from '../state/lockStore'
import { useAlbumPrefsStore } from '../state/albumPrefsStore'

/**
 * Removes everything this app keeps about the signed in account on this device: the IndexedDB
 * library cache, saved video positions, lock data (passcode, locked items and albums, biometric
 * credential) and album preferences. Used on sign out so the next person to sign in on a shared
 * browser starts clean.
 */
export async function clearAccountData(): Promise<void> {
  useLockStore.getState().resetAll()
  useAlbumPrefsStore.getState().reset()
  clearBiometricUnlock()
  await clearCache()
}
