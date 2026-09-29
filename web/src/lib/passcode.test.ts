import { describe, expect, it } from 'vitest'
import { hashPasscode, isValidPasscode, verifyPasscode } from './passcode'

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

describe('hashPasscode / verifyPasscode', () => {
  it('produces a stable 64-char hex SHA-256 digest', async () => {
    const hash = await hashPasscode('1234')
    expect(hash).toMatch(/^[0-9a-f]{64}$/)
    const hash2 = await hashPasscode('1234')
    expect(hash2).toBe(hash)
  })

  it('produces different hashes for different passcodes', async () => {
    const a = await hashPasscode('1234')
    const b = await hashPasscode('4321')
    expect(a).not.toBe(b)
  })

  it('verifyPasscode confirms a matching passcode and rejects a wrong one', async () => {
    const hash = await hashPasscode('9081')
    expect(await verifyPasscode('9081', hash)).toBe(true)
    expect(await verifyPasscode('1111', hash)).toBe(false)
  })
})
