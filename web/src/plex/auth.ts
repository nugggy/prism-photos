import { buildPlexHeaders, getClientIdentifier } from './headers'
import type { PlexPinDto, PlexUserDto } from './dto'

const PLEX_TV = 'https://plex.tv'

export interface PlexPin {
  id: number
  code: string
  expiresAt: string
  authToken: string | null
}

/** Step 1: create a PIN. */
export async function createPin(fetchImpl: typeof fetch = fetch): Promise<PlexPin> {
  const res = await fetchImpl(`${PLEX_TV}/api/v2/pins?strong=true`, {
    method: 'POST',
    headers: buildPlexHeaders(),
  })
  if (!res.ok) throw new Error(`Failed to create sign-in PIN (${res.status})`)
  const dto = (await res.json()) as PlexPinDto
  return dto
}

/** Step 2: the URL to open in a new tab for the user to authorise the PIN. */
export function buildAuthUrl(pin: PlexPin, forwardUrl?: string): string {
  const params = new URLSearchParams({
    clientID: getClientIdentifier(),
    code: pin.code,
    'context[device][product]': 'Plex Gallery',
  })
  // With forwardUrl Plex sends the browser back to the app after authorising,
  // so the whole flow can run in one tab (mobile browsers freeze background tabs).
  if (forwardUrl) params.set('forwardUrl', forwardUrl)
  return `https://app.plex.tv/auth#?${params.toString()}`
}

const PENDING_PIN_KEY = 'prism.pendingPin'

export interface PendingPin {
  id: number
  code: string
  createdAt: number
}

/** Remembers a PIN across the redirect to plex.tv and back. */
export function savePendingPin(pin: PlexPin): void {
  try {
    localStorage.setItem(PENDING_PIN_KEY, JSON.stringify({ id: pin.id, code: pin.code, createdAt: Date.now() }))
  } catch {
    // ignore
  }
}

/** Returns the pending PIN if one was started in the last 15 minutes. */
export function loadPendingPin(): PendingPin | null {
  try {
    const raw = localStorage.getItem(PENDING_PIN_KEY)
    if (!raw) return null
    const pin = JSON.parse(raw) as PendingPin
    if (!pin.id || Date.now() - pin.createdAt > 15 * 60 * 1000) {
      localStorage.removeItem(PENDING_PIN_KEY)
      return null
    }
    return pin
  } catch {
    return null
  }
}

export function clearPendingPin(): void {
  try {
    localStorage.removeItem(PENDING_PIN_KEY)
  } catch {
    // ignore
  }
}

/** Step 3: poll until authToken is set. Returns null while still pending. */
export async function pollPin(pinId: number, fetchImpl: typeof fetch = fetch): Promise<PlexPin> {
  const res = await fetchImpl(`${PLEX_TV}/api/v2/pins/${pinId}`, {
    headers: buildPlexHeaders(),
  })
  if (!res.ok) throw new Error(`Failed to poll sign-in PIN (${res.status})`)
  const dto = (await res.json()) as PlexPinDto
  return dto
}

export interface PollUntilAuthorisedOptions {
  intervalMs?: number
  timeoutMs?: number
  fetchImpl?: typeof fetch
  onTick?: (pin: PlexPin) => void
}

/** Polls pollPin every intervalMs until authToken appears or timeoutMs elapses. */
export async function pollUntilAuthorised(
  pinId: number,
  options: PollUntilAuthorisedOptions = {},
): Promise<string> {
  const { intervalMs = 2000, timeoutMs = 10 * 60 * 1000, fetchImpl = fetch, onTick } = options
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const pin = await pollPin(pinId, fetchImpl)
    onTick?.(pin)
    if (pin.authToken) return pin.authToken
    if (Date.now() > deadline) throw new Error('Sign-in timed out. Please try again.')
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
}

/** Step 4: fetch the plex.tv account for the given token. */
export async function fetchPlexUser(token: string, fetchImpl: typeof fetch = fetch): Promise<PlexUserDto> {
  const res = await fetchImpl(`${PLEX_TV}/api/v2/user`, {
    headers: buildPlexHeaders({ token }),
  })
  if (!res.ok) throw new Error(`Failed to fetch account (${res.status})`)
  return (await res.json()) as PlexUserDto
}

export async function signOut(token: string, fetchImpl: typeof fetch = fetch): Promise<void> {
  try {
    await fetchImpl(`${PLEX_TV}/api/v2/users/signout`, {
      method: 'DELETE',
      headers: buildPlexHeaders({ token }),
    })
  } catch {
    // Best effort, per docs/plex-api.md.
  }
}
