// Passcode hashing for the Lock feature. The passcode itself is never stored. New records use
// PBKDF2-SHA256 with a random 16 byte salt; records created by older builds were a bare
// SHA-256 hex digest and are upgraded the next time the right passcode is entered.
//
// This only protects the passcode at rest in this browser. Anyone with access to the device's
// storage can still clear the lock data, so locking is a privacy screen, not encryption.

export const PBKDF2_ITERATIONS = 210_000
const SALT_BYTES = 16
const KEY_BITS = 256

export interface PasscodeRecord {
  v: 2
  algo: 'PBKDF2-SHA256'
  /** Base64 encoded salt. */
  salt: string
  iterations: number
  /** Base64 encoded derived key. */
  hash: string
}

/** A stored passcode: a PBKDF2 record, or a legacy unsalted SHA-256 hex digest. */
export type StoredPasscode = PasscodeRecord | string

function bytesToHex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

function bytesToBase64(bytes: Uint8Array): string {
  let s = ''
  bytes.forEach((b) => (s += String.fromCharCode(b)))
  return btoa(s)
}

function base64ToBytes(b64: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
}

/** Compares two strings without returning early on the first difference. */
function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false
  let diff = 0
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i)
  return diff === 0
}

export function isValidPasscode(passcode: string): boolean {
  return /^\d{4,8}$/.test(passcode)
}

/** Legacy scheme: hex SHA-256 digest, kept only so older stored passcodes can be verified and upgraded. */
export async function legacyHashPasscode(passcode: string): Promise<string> {
  const data = new TextEncoder().encode(passcode)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return bytesToHex(digest)
}

async function deriveKey(passcode: string, salt: Uint8Array<ArrayBuffer>, iterations: number): Promise<Uint8Array> {
  const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(passcode), 'PBKDF2', false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations }, material, KEY_BITS)
  return new Uint8Array(bits)
}

/** Creates a salted PBKDF2-SHA256 record for storage. */
export async function createPasscodeRecord(passcode: string, iterations = PBKDF2_ITERATIONS): Promise<PasscodeRecord> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES))
  const key = await deriveKey(passcode, salt, iterations)
  return { v: 2, algo: 'PBKDF2-SHA256', salt: bytesToBase64(salt), iterations, hash: bytesToBase64(key) }
}

export function isLegacyPasscode(stored: StoredPasscode): stored is string {
  return typeof stored === 'string'
}

export interface VerifyResult {
  ok: boolean
  /** Set when the stored value used the legacy scheme and should be replaced with this record. */
  upgraded?: PasscodeRecord
}

/** Verifies a passcode against a stored value, upgrading legacy digests on success. */
export async function verifyPasscode(passcode: string, stored: StoredPasscode): Promise<VerifyResult> {
  if (isLegacyPasscode(stored)) {
    const candidate = await legacyHashPasscode(passcode)
    if (!constantTimeEqual(candidate, stored)) return { ok: false }
    return { ok: true, upgraded: await createPasscodeRecord(passcode) }
  }
  const key = await deriveKey(passcode, base64ToBytes(stored.salt), stored.iterations)
  return { ok: constantTimeEqual(bytesToBase64(key), stored.hash) }
}

// ---------------------------------------------------------------------------------------
// Attempt limiting. Wrong entries are counted in localStorage so a reload does not reset
// them. The first few attempts are free, then each wrong entry doubles the wait.
// ---------------------------------------------------------------------------------------

const ATTEMPTS_KEY = 'prism.lockAttempts'
export const FREE_ATTEMPTS = 3
const BASE_DELAY_MS = 5_000
const MAX_DELAY_MS = 15 * 60_000

interface AttemptState {
  failures: number
  lockedUntil: number
}

function readAttempts(storage: Storage | undefined): AttemptState {
  try {
    const raw = storage?.getItem(ATTEMPTS_KEY)
    if (!raw) return { failures: 0, lockedUntil: 0 }
    const parsed = JSON.parse(raw) as Partial<AttemptState>
    return { failures: Number(parsed.failures) || 0, lockedUntil: Number(parsed.lockedUntil) || 0 }
  } catch {
    return { failures: 0, lockedUntil: 0 }
  }
}

function writeAttempts(storage: Storage | undefined, state: AttemptState): void {
  try {
    storage?.setItem(ATTEMPTS_KEY, JSON.stringify(state))
  } catch {
    /* storage unavailable */
  }
}

function defaultStorage(): Storage | undefined {
  try {
    return typeof localStorage === 'undefined' ? undefined : localStorage
  } catch {
    return undefined
  }
}

/** Delay imposed after the given number of consecutive failures. */
export function delayForFailures(failures: number): number {
  if (failures < FREE_ATTEMPTS) return 0
  return Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** (failures - FREE_ATTEMPTS))
}

/** Milliseconds until another attempt is allowed (0 when allowed now). */
export function getLockoutRemainingMs(now = Date.now(), storage = defaultStorage()): number {
  return Math.max(0, readAttempts(storage).lockedUntil - now)
}

/** Records a wrong entry and returns the wait before the next attempt. */
export function recordFailedAttempt(now = Date.now(), storage = defaultStorage()): number {
  const failures = readAttempts(storage).failures + 1
  const delay = delayForFailures(failures)
  writeAttempts(storage, { failures, lockedUntil: delay > 0 ? now + delay : 0 })
  return delay
}

export function resetFailedAttempts(storage = defaultStorage()): void {
  try {
    storage?.removeItem(ATTEMPTS_KEY)
  } catch {
    /* storage unavailable */
  }
}
