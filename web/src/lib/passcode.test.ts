import { beforeEach, describe, expect, it } from 'vitest'
import {
  FREE_ATTEMPTS,
  PBKDF2_ITERATIONS,
  createPasscodeRecord,
  delayForFailures,
  getLockoutRemainingMs,
  isValidPasscode,
  legacyHashPasscode,
  recordFailedAttempt,
  resetFailedAttempts,
  verifyPasscode,
} from './passcode'

// Fewer iterations keep the tests quick; the scheme is identical.
const FAST = 1000

describe('isValidPasscode', () => {
  it('accepts 4 to 8 digit numeric codes', () => {
    expect(isValidPasscode('1234')).toBe(true)
    expect(isValidPasscode('12345678')).toBe(true)
  })

  it('rejects codes outside 4-8 digits or non numeric', () => {
    expect(isValidPasscode('123')).toBe(false)
    expect(isValidPasscode('123456789')).toBe(false)
    expect(isValidPasscode('12a4')).toBe(false)
    expect(isValidPasscode('')).toBe(false)
  })
})

describe('PBKDF2 passcode records', () => {
  it('uses at least 200k iterations by default', () => {
    expect(PBKDF2_ITERATIONS).toBeGreaterThanOrEqual(200_000)
  })

  it('stores a random 16 byte salt, the iteration count and a 32 byte key', async () => {
    const record = await createPasscodeRecord('1234', FAST)
    expect(record.v).toBe(2)
    expect(record.algo).toBe('PBKDF2-SHA256')
    expect(record.iterations).toBe(FAST)
    expect(atob(record.salt)).toHaveLength(16)
    expect(atob(record.hash)).toHaveLength(32)
  })

  it('salts every record, so the same passcode never gives the same hash', async () => {
    const a = await createPasscodeRecord('1234', FAST)
    const b = await createPasscodeRecord('1234', FAST)
    expect(a.salt).not.toBe(b.salt)
    expect(a.hash).not.toBe(b.hash)
  })

  it('verifies the right passcode and rejects a wrong one', async () => {
    const record = await createPasscodeRecord('9081', FAST)
    expect((await verifyPasscode('9081', record)).ok).toBe(true)
    expect((await verifyPasscode('1111', record)).ok).toBe(false)
  })

  it('does not ask to upgrade a current record', async () => {
    const record = await createPasscodeRecord('9081', FAST)
    expect((await verifyPasscode('9081', record)).upgraded).toBeUndefined()
  })
})

describe('legacy SHA-256 migration', () => {
  it('accepts the right passcode against an old unsalted digest and returns a PBKDF2 replacement', async () => {
    const legacy = await legacyHashPasscode('2468')
    expect(legacy).toMatch(/^[0-9a-f]{64}$/)
    const result = await verifyPasscode('2468', legacy)
    expect(result.ok).toBe(true)
    expect(result.upgraded?.algo).toBe('PBKDF2-SHA256')
    expect((await verifyPasscode('2468', result.upgraded!)).ok).toBe(true)
  })

  it('rejects a wrong passcode against an old digest without upgrading', async () => {
    const legacy = await legacyHashPasscode('2468')
    const result = await verifyPasscode('1357', legacy)
    expect(result).toEqual({ ok: false })
  })
})

describe('attempt limiting', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('allows a few free attempts, then doubles the wait for each failure up to a cap', () => {
    expect(delayForFailures(FREE_ATTEMPTS - 1)).toBe(0)
    const first = delayForFailures(FREE_ATTEMPTS)
    expect(first).toBeGreaterThan(0)
    expect(delayForFailures(FREE_ATTEMPTS + 1)).toBe(first * 2)
    expect(delayForFailures(100)).toBe(15 * 60_000)
  })

  it('persists failures in localStorage and blocks until the delay has passed', () => {
    const now = 1_000_000
    for (let i = 0; i < FREE_ATTEMPTS - 1; i++) expect(recordFailedAttempt(now)).toBe(0)
    expect(getLockoutRemainingMs(now)).toBe(0)
    const wait = recordFailedAttempt(now)
    expect(wait).toBeGreaterThan(0)
    expect(localStorage.getItem('prism.lockAttempts')).not.toBeNull()
    expect(getLockoutRemainingMs(now + 1)).toBe(wait - 1)
    expect(getLockoutRemainingMs(now + wait)).toBe(0)
  })

  it('clears the count after a reset', () => {
    for (let i = 0; i < FREE_ATTEMPTS + 2; i++) recordFailedAttempt(5000)
    resetFailedAttempts()
    expect(getLockoutRemainingMs(5000)).toBe(0)
    expect(recordFailedAttempt(5000)).toBe(0)
  })
})
