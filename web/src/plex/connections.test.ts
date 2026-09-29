import { describe, expect, it } from 'vitest'
import { chooseConnection, probeConnection, rankConnections } from './connections'
import type { PlexConnectionDto } from './dto'

const localHttps: PlexConnectionDto = {
  protocol: 'https',
  address: '192-168-1-20.abcdef.plex.direct',
  port: 32400,
  uri: 'https://192-168-1-20.abcdef.plex.direct:32400',
  local: true,
  relay: false,
  IPv6: false,
}
const localHttp: PlexConnectionDto = {
  protocol: 'http',
  address: '192.168.1.20',
  port: 32400,
  uri: 'http://192.168.1.20:32400',
  local: true,
  relay: false,
  IPv6: false,
}
const remoteHttps: PlexConnectionDto = {
  protocol: 'https',
  address: '203-0-113-5.abcdef.plex.direct',
  port: 32400,
  uri: 'https://203-0-113-5.abcdef.plex.direct:32400',
  local: false,
  relay: false,
  IPv6: false,
}
const relayHttps: PlexConnectionDto = {
  protocol: 'https',
  address: 'abcdef.plex.direct',
  port: 443,
  uri: 'https://abcdef.plex.direct:443',
  local: false,
  relay: true,
  IPv6: false,
}

const all = [localHttps, localHttp, remoteHttps, relayHttps]

describe('rankConnections', () => {
  it('AUTO: local non-relay first, then remote, then relay; ties prefer https', () => {
    const ranked = rankConnections(all, 'AUTO', false)
    expect(ranked.map((c) => c.uri)).toEqual([localHttps.uri, localHttp.uri, remoteHttps.uri, relayHttps.uri])
  })

  it('LAN: tries local first, only considers remote/relay when no local exists', () => {
    const ranked = rankConnections(all, 'LAN', false)
    expect(ranked[0]).toBe(localHttps)
    expect(ranked[1]).toBe(localHttp)

    const noLocal = rankConnections([remoteHttps, relayHttps], 'LAN', false)
    expect(noLocal.map((c) => c.uri)).toEqual([remoteHttps.uri, relayHttps.uri])
  })

  it('REMOTE: skips local connections entirely', () => {
    const ranked = rankConnections(all, 'REMOTE', false)
    expect(ranked.some((c) => c.local)).toBe(false)
    expect(ranked.map((c) => c.uri)).toEqual([remoteHttps.uri, relayHttps.uri])
  })

  it('on a secure page, excludes plain http connections', () => {
    const ranked = rankConnections(all, 'AUTO', true)
    expect(ranked.some((c) => c.protocol === 'http')).toBe(false)
    expect(ranked.map((c) => c.uri)).toEqual([localHttps.uri, remoteHttps.uri, relayHttps.uri])
  })

  it('on an insecure page, keeps http connections', () => {
    const ranked = rankConnections(all, 'AUTO', false)
    expect(ranked.some((c) => c.protocol === 'http')).toBe(true)
  })
})

describe('probeConnection', () => {
  it('returns reachable with the machine identifier on success', async () => {
    const fetchImpl = (async () =>
      new Response(JSON.stringify({ MediaContainer: { machineIdentifier: 'abc123' } }), { status: 200 })) as typeof fetch
    const result = await probeConnection(localHttps, 2500, fetchImpl)
    expect(result.reachable).toBe(true)
    expect(result.machineIdentifier).toBe('abc123')
  })

  it('returns unreachable on a non-ok response', async () => {
    const fetchImpl = (async () => new Response('', { status: 500 })) as typeof fetch
    const result = await probeConnection(localHttps, 2500, fetchImpl)
    expect(result.reachable).toBe(false)
  })

  it('returns unreachable when the fetch throws (timeout/network error)', async () => {
    const fetchImpl = (async () => {
      throw new Error('network error')
    }) as typeof fetch
    const result = await probeConnection(localHttps, 2500, fetchImpl)
    expect(result.reachable).toBe(false)
  })
})

describe('chooseConnection', () => {
  it('prefers a reachable manual URL over discovered connections', async () => {
    const fetchImpl = (async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.startsWith('http://10.0.0.5:32400')) {
        return new Response(JSON.stringify({ MediaContainer: { machineIdentifier: 'manual' } }), { status: 200 })
      }
      return new Response('', { status: 500 })
    }) as typeof fetch

    const result = await chooseConnection(all, { mode: 'AUTO', manualUrl: 'http://10.0.0.5:32400', fetchImpl })
    expect(result.manual).toBe(true)
    expect(result.uri).toBe('http://10.0.0.5:32400')
  })

  it('falls back to the best reachable ranked connection when nothing manual is set', async () => {
    const fetchImpl = (async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.startsWith(remoteHttps.uri)) {
        return new Response(JSON.stringify({ MediaContainer: { machineIdentifier: 'remote' } }), { status: 200 })
      }
      return new Response('', { status: 500 })
    }) as typeof fetch

    const result = await chooseConnection(all, { mode: 'AUTO', fetchImpl })
    expect(result.manual).toBe(false)
    expect(result.uri).toBe(remoteHttps.uri)
  })

  it('returns no connection when nothing is reachable', async () => {
    const fetchImpl = (async () => new Response('', { status: 500 })) as typeof fetch
    const result = await chooseConnection(all, { mode: 'AUTO', fetchImpl })
    expect(result.connection).toBeNull()
    expect(result.uri).toBe('')
  })
})
