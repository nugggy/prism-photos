import { buildPlexHeaders } from './headers'
import type { PlexResourceDto } from './dto'

const PLEX_TV = 'https://plex.tv'

export interface PlexServer {
  name: string
  product: string
  clientIdentifier: string
  owned: boolean
  accessToken: string
  connections: PlexResourceDto['connections']
}

/** Fetches all plex.tv resources and keeps only those that provide "server". */
export async function fetchServers(token: string, fetchImpl: typeof fetch = fetch): Promise<PlexServer[]> {
  const params = new URLSearchParams({ includeHttps: '1', includeRelay: '1', includeIPv6: '1' })
  const res = await fetchImpl(`${PLEX_TV}/api/v2/resources?${params.toString()}`, {
    headers: buildPlexHeaders({ token }),
  })
  if (!res.ok) throw new Error(`Failed to discover servers (${res.status})`)
  const dtos = (await res.json()) as PlexResourceDto[]
  return dtos
    .filter((d) => d.provides.split(',').map((p) => p.trim()).includes('server'))
    .map((d) => ({
      name: d.name,
      product: d.product,
      clientIdentifier: d.clientIdentifier,
      owned: d.owned,
      accessToken: d.accessToken ?? token,
      connections: d.connections,
    }))
}
