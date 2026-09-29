import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { buildAuthUrl, clearPendingPin, createPin, fetchPlexUser, loadPendingPin, pollUntilAuthorised, savePendingPin } from '../plex/auth'
import { fetchIdentity, verifyManualServer } from '../plex/api'
import { useSessionStore } from '../state/sessionStore'

export function SignIn(): React.ReactElement {
  const navigate = useNavigate()
  const setAccount = useSessionStore((s) => s.setAccount)
  const selectServer = useSessionStore((s) => s.selectServer)
  const setActiveConnection = useSessionStore((s) => s.setActiveConnection)

  const [mode, setMode] = useState<'plex' | 'manual'>('plex')

  const [pinCode, setPinCode] = useState<string | null>(null)
  const [signingIn, setSigningIn] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const cancelRef = useRef(false)

  const [manualUrl, setManualUrl] = useState('')
  const [manualToken, setManualToken] = useState('')
  const [manualBusy, setManualBusy] = useState(false)

  /** Finishes sign in once a PIN has been authorised: fetch the account, store it, move on. */
  const completeWithPin = useCallback(
    async (pinId: number) => {
      const token = await pollUntilAuthorised(pinId, {
        timeoutMs: 5 * 60 * 1000,
        onTick: () => {
          if (cancelRef.current) throw new Error('Sign-in cancelled.')
        },
      })
      const user = await fetchPlexUser(token)
      clearPendingPin()
      setAccount(token, user.username || user.title || user.email)
      navigate('/servers')
    },
    [navigate, setAccount],
  )

  // Same tab flow: create a PIN, remember it, send the browser to plex.tv, and let Plex
  // forward back here. Mobile browsers freeze or reload background tabs, so a popup plus
  // polling in the original tab is unreliable.
  const startPlexSignIn = useCallback(async () => {
    setError(null)
    setSigningIn(true)
    cancelRef.current = false
    try {
      const pin = await createPin()
      savePendingPin(pin)
      setPinCode(pin.code)
      const forwardUrl = `${window.location.origin}${window.location.pathname}${window.location.search}#/signin`
      window.location.assign(buildAuthUrl(pin, forwardUrl))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Sign-in failed.')
      setSigningIn(false)
      setPinCode(null)
    }
  }, [])

  // Coming back from plex.tv (or after the tab was reloaded): resume the pending PIN.
  const resumedPinRef = useRef<number | null>(null)
  useEffect(() => {
    const pending = loadPendingPin()
    if (!pending) return
    // Only one poll loop per PIN, even if React re-runs this effect.
    if (resumedPinRef.current === pending.id) return
    resumedPinRef.current = pending.id
    cancelRef.current = false
    setSigningIn(true)
    setPinCode(pending.code)
    completeWithPin(pending.id)
      .catch((e) => {
        clearPendingPin()
        setError(e instanceof Error ? e.message : 'Sign-in failed.')
      })
      .finally(() => {
        setSigningIn(false)
        setPinCode(null)
      })
  }, [completeWithPin])

  const cancelPlexSignIn = useCallback(() => {
    cancelRef.current = true
    clearPendingPin()
    setSigningIn(false)
    setPinCode(null)
  }, [])

  const submitManual = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault()
      setError(null)
      setManualBusy(true)
      try {
        const baseUrl = manualUrl.trim().replace(/\/$/, '')
        const server = { baseUrl, token: manualToken.trim() }
        await verifyManualServer(server)
        const machineId = await fetchIdentity(baseUrl)
        selectServer('manual', server.token, machineId)
        setActiveConnection({ baseUrl, kind: 'manual' })
        navigate('/servers')
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Could not connect to that server.')
      } finally {
        setManualBusy(false)
      }
    },
    [manualUrl, manualToken, navigate, selectServer, setActiveConnection],
  )

  return (
    <div className="center-screen">
      <div className="card" style={{ width: '100%', maxWidth: 420 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
          <img src="logo.svg" alt="" width={44} height={44} />
          <div>
            <h1 style={{ margin: 0, fontSize: 22 }}>Plex Gallery</h1>
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>A better photo library for Plex</p>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
          <button className={`chip${mode === 'plex' ? ' active' : ''}`} onClick={() => setMode('plex')} type="button">
            Sign in with Plex
          </button>
          <button className={`chip${mode === 'manual' ? ' active' : ''}`} onClick={() => setMode('manual')} type="button">
            Connect manually
          </button>
        </div>

        {mode === 'plex' && (
          <div>
            <p className="muted" style={{ fontSize: 14 }}>
              Sign in with your Plex account. You will be taken to plex.tv to authorise Plex Gallery and brought straight back.
            </p>
            {pinCode && (
              <p style={{ fontSize: 14 }}>
                Waiting for authorisation. If plex.tv asks for a code, it is <strong>{pinCode}</strong>.{' '}
                <button type="button" className="btn" onClick={cancelPlexSignIn} style={{ marginLeft: 8 }}>
                  Cancel
                </button>
              </p>
            )}
            {error && <p style={{ color: 'var(--danger)', fontSize: 14 }}>{error}</p>}
            <button className="btn btn-primary" onClick={() => void startPlexSignIn()} disabled={signingIn} style={{ width: '100%', justifyContent: 'center' }}>
              {signingIn ? 'Waiting for authorisation' : 'Sign in with Plex'}
            </button>
          </div>
        )}

        {mode === 'manual' && (
          <form onSubmit={(e) => void submitManual(e)}>
            <p className="muted" style={{ fontSize: 14 }}>
              For servers not linked to a Plex account, or for a direct LAN address.
            </p>
            <label htmlFor="manual-url" style={{ fontSize: 13 }}>
              Server URL
            </label>
            <input
              id="manual-url"
              className="input"
              placeholder="http://192.168.1.20:32400"
              value={manualUrl}
              onChange={(e) => setManualUrl(e.target.value)}
              required
              style={{ marginBottom: 10, marginTop: 4 }}
            />
            <label htmlFor="manual-token" style={{ fontSize: 13 }}>
              Access token
            </label>
            <input
              id="manual-token"
              className="input"
              placeholder="X-Plex-Token"
              value={manualToken}
              onChange={(e) => setManualToken(e.target.value)}
              required
              style={{ marginBottom: 10, marginTop: 4 }}
            />
            {error && <p style={{ color: 'var(--danger)', fontSize: 14 }}>{error}</p>}
            <button className="btn btn-primary" type="submit" disabled={manualBusy} style={{ width: '100%', justifyContent: 'center' }}>
              {manualBusy ? 'Connecting' : 'Connect'}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
