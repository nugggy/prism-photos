// Optional WebAuthn platform authenticator unlock ("device biometrics"), used by the
// Lock feature as a faster alternative to typing the passcode. Falls back silently
// wherever unsupported.

const CREDENTIAL_ID_KEY = 'prism.webauthnCredentialId'
const RP_NAME = 'Plex Gallery'

export async function isWebAuthnAvailable(): Promise<boolean> {
  if (!window.PublicKeyCredential) return false
  try {
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()
  } catch {
    return false
  }
}

function randomChallenge(): BufferSource {
  const arr = new Uint8Array(32)
  crypto.getRandomValues(arr)
  return arr as BufferSource
}

export async function registerBiometricUnlock(username: string): Promise<boolean> {
  if (!(await isWebAuthnAvailable())) return false
  try {
    const userId = crypto.getRandomValues(new Uint8Array(16))
    const credential = await navigator.credentials.create({
      publicKey: {
        challenge: randomChallenge(),
        rp: { name: RP_NAME },
        user: { id: userId, name: username, displayName: username },
        pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
        authenticatorSelection: { authenticatorAttachment: 'platform', userVerification: 'required' },
        timeout: 60000,
      },
    })
    if (!credential) return false
    const rawId = (credential as PublicKeyCredential).rawId
    const id = btoa(String.fromCharCode(...new Uint8Array(rawId)))
    localStorage.setItem(CREDENTIAL_ID_KEY, id)
    return true
  } catch {
    return false
  }
}

export function hasBiometricUnlock(): boolean {
  try {
    return !!localStorage.getItem(CREDENTIAL_ID_KEY)
  } catch {
    return false
  }
}

export function clearBiometricUnlock(): void {
  try {
    localStorage.removeItem(CREDENTIAL_ID_KEY)
  } catch {
    /* ignore */
  }
}

export async function verifyBiometricUnlock(): Promise<boolean> {
  const stored = (() => {
    try {
      return localStorage.getItem(CREDENTIAL_ID_KEY)
    } catch {
      return null
    }
  })()
  if (!stored || !(await isWebAuthnAvailable())) return false
  try {
    const idBytes = Uint8Array.from(atob(stored), (c) => c.charCodeAt(0))
    const assertion = await navigator.credentials.get({
      publicKey: {
        challenge: randomChallenge(),
        allowCredentials: [{ id: idBytes, type: 'public-key' }],
        userVerification: 'required',
        timeout: 60000,
      },
    })
    return !!assertion
  } catch {
    return false
  }
}
