// Passcode hashing for the Lock feature. Uses SubtleCrypto SHA-256; the passcode itself
// is never stored, only its hex digest.

function bytesToHex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

export function isValidPasscode(passcode: string): boolean {
  return /^\d{4,8}$/.test(passcode)
}

/** Hashes a passcode to a hex SHA-256 digest for storage/comparison. */
export async function hashPasscode(passcode: string): Promise<string> {
  const data = new TextEncoder().encode(passcode)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return bytesToHex(digest)
}

export async function verifyPasscode(passcode: string, hash: string): Promise<boolean> {
  const candidate = await hashPasscode(passcode)
  return candidate === hash
}
