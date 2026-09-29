import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchServers, type PlexServer } from '../plex/resources'
import { probeConnection, rankConnections, type ConnectionMode } from '../plex/connections'
import { fetchIdentity, fetchSections } from '../plex/api'
import type { Library } from '../plex/model'
import { useSessionStore } from '../state/sessionStore'
import { useSettingsStore } from '../state/settingsStore'
import { useServer } from '../lib/useServer'

interface ProbedConnection {
  uri: string
  local: boolean
  relay: boolean
  reachable: boolean | 'checking'
}

export function ServerPicker(): React.ReactElement {
  const navigate = useNavigate()
  const accountToken = useSessionStore((s) => s.accountToken)
  const servers = useSessionStore((s) => s.servers)
  const setServers = useSessionStore((s) => s.setServers)
  const selectServer = useSessionStore((s) => s.selectServer)
  const setActiveConnection = useSessionStore((s) => s.setActiveConnection)
  const libraries = useSessionStore((s) => s.libraries)
  const setLibraries = useSessionStore((s) => s.setLibraries)
  const selectSection = useSessionStore((s) => s.selectSection)
  const activeConnection = useSessionStore((s) => s.activeConnection)
  const machineId = useSessionStore((s) => s.machineId)
  const setMachineId = useSessionStore((s) => s.setMachineId)

  const connectionMode = useSettingsStore((s) => s.connectionMode)
  const setConnectionMode = useSettingsStore((s) => s.setConnectionMode)
  const manualUrl = useSettingsStore((s) => s.manualUrl)
  const setManualUrl = useSettingsStore((s) => s.setManualUrl)

  const [loadingServers, setLoadingServers] = useState(false)
  const [probes, setProbes] = useState<Record<string, ProbedConnection[]>>({})
  const [error, setError] = useState<string | null>(null)
  const [connecting, setConnecting] = useState<string | null>(null)

  const server = useServer()
  const [librariesLoading, setLibrariesLoading] = useState(false)

  useEffect(() => {
    if (!accountToken || servers.length > 0 || activeConnection) return
    setLoadingServers(true)
    fetchServers(accountToken)
      .then(setServers)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load servers.'))
      .finally(() => setLoadingServers(false))
  }, [accountToken, servers.length, activeConnection, setServers])

  useEffect(() => {
    if (activeConnection || servers.length === 0) return
    const isSecurePage = typeof window !== 'undefined' && window.location.protocol === 'https:'
    servers.forEach((s) => {
      const ranked = rankConnections(s.connections, connectionMode, isSecurePage)
      setProbes((prev) => ({
        ...prev,
        [s.clientIdentifier]: ranked.map((c) => ({ uri: c.uri, local: c.local, relay: c.relay, reachable: 'checking' })),
      }))
      ranked.forEach((c) => {
        void probeConnection(c).then((result) => {
          setProbes((prev) => ({
            ...prev,
            [s.clientIdentifier]: (prev[s.clientIdentifier] ?? []).map((p) =>
              p.uri === c.uri ? { ...p, reachable: result.reachable } : p,
            ),
          }))
        })
      })
    })
  }, [servers, connectionMode, activeConnection])

  const connectToServer = useCallback(
    async (s: PlexServer) => {
      setError(null)
      setConnecting(s.clientIdentifier)
      try {
        const isSecurePage = typeof window !== 'undefined' && window.location.protocol === 'https:'
        const ranked = rankConnections(s.connections, connectionMode, isSecurePage)
        const candidates = manualUrl ? [{ uri: manualUrl.replace(/\/$/, ''), local: true, relay: false }, ...ranked] : ranked
        let chosen: { uri: string; local: boolean; relay: boolean } | null = null
        for (const c of candidates) {
          const result = await probeConnection({ protocol: '', address: '', port: 0, uri: c.uri, local: c.local, relay: c.relay, IPv6: false })
          if (result.reachable) {
            chosen = c
            break
          }
        }
        if (!chosen) throw new Error('Could not reach this server on any known connection.')
        selectServer(s.clientIdentifier, s.accessToken, '')
        const kind = manualUrl && chosen.uri === manualUrl.replace(/\/$/, '') ? 'manual' : chosen.relay ? 'relay' : chosen.local ? 'local' : 'remote'
        setActiveConnection({ baseUrl: chosen.uri, kind })
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Could not connect to that server.')
      } finally {
        setConnecting(null)
      }
    },
    [connectionMode, manualUrl, selectServer, setActiveConnection],
  )

  useEffect(() => {
    if (!server || libraries.length > 0) return
    setLibrariesLoading(true)
    fetchSections(server)
      .then(setLibraries)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load libraries.'))
      .finally(() => setLibrariesLoading(false))
  }, [server, libraries.length, setLibraries])

  useEffect(() => {
    if (!activeConnection || machineId) return
    fetchIdentity(activeConnection.baseUrl)
      .then(setMachineId)
      .catch(() => {
        /* not critical; Plex web links just won't resolve */
      })
  }, [activeConnection, machineId, setMachineId])

  const pickLibrary = useCallback(
    (lib: Library) => {
      selectSection(lib.key)
      navigate('/photos')
    },
    [navigate, selectSection],
  )

  if (activeConnection && server) {
    return (
      <div className="page">
        <div className="page-header">
          <h1 className="page-title">Choose a library</h1>
        </div>
        {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
        {librariesLoading && <div className="spinner" />}
        {!librariesLoading && libraries.length === 0 && <p className="muted">No photo libraries were found on this server.</p>}
        <div style={{ display: 'grid', gap: 10, gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
          {libraries.map((lib) => (
            <button key={lib.key} className="card" style={{ textAlign: 'left', cursor: 'pointer' }} onClick={() => pickLibrary(lib)}>
              <strong>{lib.title}</strong>
              <div className="muted" style={{ fontSize: 13 }}>
                Photo library
              </div>
            </button>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Choose a server</h1>
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
        {(['AUTO', 'LAN', 'REMOTE'] as ConnectionMode[]).map((m) => (
          <button key={m} className={`chip${connectionMode === m ? ' active' : ''}`} onClick={() => setConnectionMode(m)} type="button">
            {m === 'AUTO' ? 'Auto' : m === 'LAN' ? 'Same network as server' : 'Remote only'}
          </button>
        ))}
      </div>
      <p className="muted" style={{ fontSize: 13, marginTop: -8 }}>
        On a page loaded over https, plain http server addresses cannot be used (browsers block mixed content). Prism prefers
        *.plex.direct https addresses, which also resolve to the LAN IP.
      </p>

      <input
        className="input"
        placeholder="Manual LAN address override, e.g. http://192.168.1.20:32400"
        value={manualUrl}
        onChange={(e) => setManualUrl(e.target.value)}
        style={{ marginBottom: 16 }}
      />

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loadingServers && <div className="spinner" />}

      <div style={{ display: 'grid', gap: 10 }}>
        {servers.map((s) => (
          <div key={s.clientIdentifier} className="card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
              <div>
                <strong>{s.name}</strong>
                <div className="muted" style={{ fontSize: 13 }}>
                  {s.owned ? 'Your server' : 'Shared with you'}
                </div>
              </div>
              <button className="btn btn-primary" onClick={() => void connectToServer(s)} disabled={connecting === s.clientIdentifier}>
                {connecting === s.clientIdentifier ? 'Connecting…' : 'Connect'}
              </button>
            </div>
            <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 4 }}>
              {(probes[s.clientIdentifier] ?? []).map((p) => (
                <div key={p.uri} className="muted" style={{ fontSize: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: 999,
                      background: p.reachable === 'checking' ? 'var(--text-muted)' : p.reachable ? 'var(--success)' : 'var(--danger)',
                      display: 'inline-block',
                    }}
                  />
                  <span>{p.uri}</span>
                  <span>({p.relay ? 'relay' : p.local ? 'local' : 'remote'})</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
