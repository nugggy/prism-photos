import type { PlexConnectionDto } from './dto'

export type ConnectionMode = 'AUTO' | 'LAN' | 'REMOTE'

export interface RankedConnection {
  connection: PlexConnectionDto
  rank: number
}

/**
 * Pure ranking function, unit testable without a network.
 *
 * Rules (docs/plex-api.md):
 * - AUTO: local non-relay first, then remote non-relay, then relay. Ties prefer https.
 * - LAN: local first; remote/relay only considered if no local connection is present.
 * - REMOTE: local connections are skipped entirely.
 *
 * `isSecurePage` marks that the page itself was loaded over https, in which case plain
 * http connections are excluded (mixed content) unless the page is not secure.
 */
export function rankConnections(
  connections: PlexConnectionDto[],
  mode: ConnectionMode,
  isSecurePage: boolean,
): PlexConnectionDto[] {
  let candidates = connections
  if (isSecurePage) {
    candidates = candidates.filter((c) => c.protocol === 'https')
  }

  if (mode === 'REMOTE') {
    candidates = candidates.filter((c) => !c.local)
  }

  if (mode === 'LAN') {
    const hasLocal = candidates.some((c) => c.local && !c.relay)
    if (hasLocal) {
      candidates = candidates.filter((c) => c.local)
    }
    // else fall through to remote/relay
  }

  const scoreOf = (c: PlexConnectionDto): number => {
    // Lower is better.
    let base: number
    if (c.local && !c.relay) base = 0
    else if (!c.local && !c.relay) base = 1
    else base = 2 // relay
    const httpsBonus = c.protocol === 'https' ? 0 : 0.5
    return base + httpsBonus
  }

  return [...candidates].sort((a, b) => scoreOf(a) - scoreOf(b))
}

export interface ProbeResult {
  connection: PlexConnectionDto
  reachable: boolean
  machineIdentifier?: string
  latencyMs?: number
}

/** Probes a single connection's /identity endpoint with a timeout, never throwing. */
export async function probeConnection(
  connection: PlexConnectionDto,
  timeoutMs = 2500,
  fetchImpl: typeof fetch = fetch,
): Promise<ProbeResult> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  const started = Date.now()
  try {
    const res = await fetchImpl(`${connection.uri}/identity`, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) return { connection, reachable: false }
    const json = (await res.json()) as { MediaContainer?: { machineIdentifier?: string } }
    return {
      connection,
      reachable: true,
      machineIdentifier: json.MediaContainer?.machineIdentifier,
      latencyMs: Date.now() - started,
    }
  } catch {
    return { connection, reachable: false }
  } finally {
    clearTimeout(timer)
  }
}

export interface ChooseConnectionOptions {
  mode: ConnectionMode
  manualUrl?: string
  isSecurePage?: boolean
  timeoutMs?: number
  fetchImpl?: typeof fetch
}

export interface ChooseConnectionResult {
  uri: string
  connection: PlexConnectionDto | null
  manual: boolean
  allProbes: ProbeResult[]
}

function manualConnectionFrom(url: string): PlexConnectionDto {
  let protocol = 'http'
  try {
    protocol = new URL(url).protocol.replace(':', '')
  } catch {
    /* ignore, keep default */
  }
  return { protocol, address: url, port: 0, uri: url.replace(/\/$/, ''), local: true, relay: false, IPv6: false }
}

/**
 * Probes ranked connections in parallel and returns the best reachable one.
 * The manual URL (if set) is always tried first, in every mode.
 */
export async function chooseConnection(
  connections: PlexConnectionDto[],
  options: ChooseConnectionOptions,
): Promise<ChooseConnectionResult> {
  const { mode, manualUrl, isSecurePage = false, timeoutMs = 2500, fetchImpl = fetch } = options

  if (manualUrl) {
    const manualConn = manualConnectionFrom(manualUrl)
    const probe = await probeConnection(manualConn, timeoutMs, fetchImpl)
    if (probe.reachable) {
      return { uri: manualConn.uri, connection: manualConn, manual: true, allProbes: [probe] }
    }
  }

  const ranked = rankConnections(connections, mode, isSecurePage)
  const probes = await Promise.all(ranked.map((c) => probeConnection(c, timeoutMs, fetchImpl)))
  const reachable = probes.filter((p) => p.reachable)

  if (reachable.length === 0) {
    return { uri: '', connection: null, manual: false, allProbes: probes }
  }

  // Re-rank reachable-only results by the same order they were probed (already ranked).
  const best = reachable[0]
  return { uri: best.connection.uri, connection: best.connection, manual: false, allProbes: probes }
}
