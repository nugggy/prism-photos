// Plex identity headers, shared by every request to plex.tv and to a server.
// See docs/plex-api.md "Required headers".

const CLIENT_ID_KEY = 'prism.clientIdentifier'

function randomUuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  // Fallback UUID v4 generator for environments without crypto.randomUUID.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/** Returns the stable per-install client identifier, generating and persisting one on first use. */
export function getClientIdentifier(): string {
  try {
    const existing = localStorage.getItem(CLIENT_ID_KEY)
    if (existing) return existing
    const created = randomUuid()
    localStorage.setItem(CLIENT_ID_KEY, created)
    return created
  } catch {
    // localStorage unavailable (private mode, SSR, etc). Fall back to a session-only id.
    return randomUuid()
  }
}

export const APP_VERSION = '1.0.0'

export interface PlexHeadersOptions {
  token?: string
  accept?: string
}

/** Builds the standard Plex identity headers. Pass a token once one is available. */
export function buildPlexHeaders(options: PlexHeadersOptions = {}): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: options.accept ?? 'application/json',
    'X-Plex-Product': 'Prism',
    'X-Plex-Version': APP_VERSION,
    'X-Plex-Client-Identifier': getClientIdentifier(),
    'X-Plex-Platform': 'Web',
    'X-Plex-Platform-Version': typeof navigator !== 'undefined' ? navigator.userAgent : 'unknown',
    'X-Plex-Device': typeof navigator !== 'undefined' ? navigator.userAgent : 'Browser',
    'X-Plex-Device-Name': 'Prism',
  }
  if (options.token) {
    headers['X-Plex-Token'] = options.token
  }
  return headers
}
