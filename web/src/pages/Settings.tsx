import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSettingsStore, type ThemePreference, type TranscodePreference } from '../state/settingsStore'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { signOut } from '../plex/auth'
import { probeConnection, type ConnectionMode } from '../plex/connections'
import { clearCache } from '../lib/cache'
import { registerBiometricUnlock, hasBiometricUnlock, clearBiometricUnlock, isWebAuthnAvailable } from '../lib/webauthn'
import { Icon } from '../components/Icon'

export function Settings(): React.ReactElement {
  const navigate = useNavigate()
  const theme = useSettingsStore((s) => s.theme)
  const setTheme = useSettingsStore((s) => s.setTheme)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const connectionMode = useSettingsStore((s) => s.connectionMode)
  const setConnectionMode = useSettingsStore((s) => s.setConnectionMode)
  const manualUrl = useSettingsStore((s) => s.manualUrl)
  const setManualUrl = useSettingsStore((s) => s.setManualUrl)
  const transcodePreference = useSettingsStore((s) => s.transcodePreference)
  const setTranscodePreference = useSettingsStore((s) => s.setTranscodePreference)
  const slideshowIntervalSec = useSettingsStore((s) => s.slideshowIntervalSec)
  const setSlideshowInterval = useSettingsStore((s) => s.setSlideshowInterval)

  const username = useSessionStore((s) => s.username)
  const accountToken = useSessionStore((s) => s.accountToken)
  const activeConnection = useSessionStore((s) => s.activeConnection)
  const sessionSignOut = useSessionStore((s) => s.signOut)

  const hasPasscode = useLockStore((s) => s.hasPasscode)
  const setPasscode = useLockStore((s) => s.setPasscode)
  const clearPasscode = useLockStore((s) => s.clearPasscode)

  const [reconnectStatus, setReconnectStatus] = useState<string | null>(null)
  const [newPasscode, setNewPasscode] = useState('')
  const [cacheCleared, setCacheCleared] = useState(false)
  const [biometricsOn, setBiometricsOn] = useState(hasBiometricUnlock())
  const [biometricsAvailable] = useState(() => typeof window !== 'undefined')

  const handleReconnect = async () => {
    if (!activeConnection) return
    setReconnectStatus('Checking…')
    const result = await probeConnection({ protocol: '', address: '', port: 0, uri: activeConnection.baseUrl, local: true, relay: false, IPv6: false })
    setReconnectStatus(result.reachable ? 'Connected.' : 'Could not reach the server.')
  }

  const handleSignOut = async () => {
    if (accountToken) await signOut(accountToken)
    sessionSignOut()
    navigate('/signin')
  }

  const handleClearCache = async () => {
    await clearCache()
    setCacheCleared(true)
  }

  const handleToggleBiometrics = async () => {
    if (biometricsOn) {
      clearBiometricUnlock()
      setBiometricsOn(false)
      return
    }
    if (!(await isWebAuthnAvailable())) return
    const ok = await registerBiometricUnlock(username ?? 'Prism user')
    setBiometricsOn(ok)
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Settings</h1>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <h2 style={{ fontSize: 15, marginTop: 0 }}>Appearance</h2>
        <label className="muted" style={{ fontSize: 13 }}>
          Theme
        </label>
        <div style={{ display: 'flex', gap: 8, margin: '6px 0 14px' }}>
          {(['system', 'light', 'dark'] as ThemePreference[]).map((t) => (
            <button key={t} className={`chip${theme === t ? ' active' : ''}`} onClick={() => setTheme(t)}>
              {t === 'system' ? 'System' : t === 'light' ? 'Light' : 'Dark'}
            </button>
          ))}
        </div>
        <label className="muted" style={{ fontSize: 13 }}>
          Default grid density ({density} columns)
        </label>
        <input type="range" min={2} max={8} value={density} onChange={(e) => setDensity(Number(e.target.value))} style={{ width: '100%' }} />
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <h2 style={{ fontSize: 15, marginTop: 0 }}>Connection</h2>
        <p className="muted" style={{ fontSize: 13 }}>
          Active: {activeConnection?.baseUrl ?? 'Not connected'} ({activeConnection?.kind ?? 'none'})
        </p>
        <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
          {(['AUTO', 'LAN', 'REMOTE'] as ConnectionMode[]).map((m) => (
            <button key={m} className={`chip${connectionMode === m ? ' active' : ''}`} onClick={() => setConnectionMode(m)}>
              {m === 'AUTO' ? 'Auto' : m === 'LAN' ? 'Same network' : 'Remote only'}
            </button>
          ))}
        </div>
        <input className="input" placeholder="Manual LAN address override" value={manualUrl} onChange={(e) => setManualUrl(e.target.value)} style={{ marginBottom: 10 }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button className="btn" onClick={() => void handleReconnect()}>
            Reconnect now
          </button>
          {reconnectStatus && <span className="muted" style={{ fontSize: 13 }}>{reconnectStatus}</span>}
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <h2 style={{ fontSize: 15, marginTop: 0 }}>Playback</h2>
        <label className="muted" style={{ fontSize: 13 }}>
          Video transcode preference
        </label>
        <div style={{ display: 'flex', gap: 8, margin: '6px 0 14px' }}>
          {(['auto', 'direct', 'hls'] as TranscodePreference[]).map((p) => (
            <button key={p} className={`chip${transcodePreference === p ? ' active' : ''}`} onClick={() => setTranscodePreference(p)}>
              {p === 'auto' ? 'Auto' : p === 'direct' ? 'Direct play' : 'Force transcode'}
            </button>
          ))}
        </div>
        <label className="muted" style={{ fontSize: 13 }}>
          Slideshow interval ({slideshowIntervalSec}s)
        </label>
        <input type="range" min={2} max={20} value={slideshowIntervalSec} onChange={(e) => setSlideshowInterval(Number(e.target.value))} style={{ width: '100%' }} />
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <h2 style={{ fontSize: 15, marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="lock" size={16} /> Lock
        </h2>
        {hasPasscode() ? (
          <button className="btn btn-danger" onClick={clearPasscode}>
            Remove passcode
          </button>
        ) : (
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              className="input"
              type="password"
              inputMode="numeric"
              placeholder="New 4-8 digit passcode"
              value={newPasscode}
              onChange={(e) => setNewPasscode(e.target.value.replace(/\D/g, '').slice(0, 8))}
            />
            <button className="btn btn-primary" disabled={newPasscode.length < 4} onClick={() => void setPasscode(newPasscode)}>
              Set
            </button>
          </div>
        )}
        {biometricsAvailable && (
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12, fontSize: 13 }}>
            <input type="checkbox" checked={biometricsOn} onChange={() => void handleToggleBiometrics()} />
            Use device biometrics to unlock
          </label>
        )}
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <h2 style={{ fontSize: 15, marginTop: 0 }}>Storage</h2>
        <button className="btn" onClick={() => void handleClearCache()}>
          Clear cache
        </button>
        {cacheCleared && <span className="muted" style={{ marginLeft: 10, fontSize: 13 }}>Cleared.</span>}
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <h2 style={{ fontSize: 15, marginTop: 0 }}>Account</h2>
        <p className="muted" style={{ fontSize: 13 }}>{username ?? 'Manual connection'}</p>
        <button className="btn btn-danger" onClick={() => void handleSignOut()}>
          Sign out
        </button>
      </div>

      <div className="card">
        <h2 style={{ fontSize: 15, marginTop: 0 }}>About</h2>
        <p className="muted" style={{ fontSize: 13 }}>
          Prism is not affiliated with Plex Inc. Prism is a client for your own Plex Media Server; Plex remains the source of
          truth for your library.
        </p>
      </div>
    </div>
  )
}
