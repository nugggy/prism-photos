import { useEffect, useState } from 'react'
import { useLockStore } from '../state/lockStore'
import { hasBiometricUnlock, isWebAuthnAvailable } from '../lib/webauthn'
import { getLockoutRemainingMs } from '../lib/passcode'
import { Icon } from './Icon'

export interface PasscodeGateProps {
  /** Line shown above the passcode field. */
  message?: string
  /** Render as a bare card (for use inside another page) instead of a full page. */
  inline?: boolean
}

function waitMessage(ms: number): string {
  const seconds = Math.ceil(ms / 1000)
  if (seconds < 60) return `Too many attempts. Try again in ${seconds} second${seconds === 1 ? '' : 's'}.`
  const minutes = Math.ceil(seconds / 60)
  return `Too many attempts. Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`
}

export function PasscodeGate({ message = 'Enter your passcode to view locked items.', inline = false }: PasscodeGateProps): React.ReactElement {
  // Select data, not the store's helper functions: functions are stable references, so a
  // component selecting one would never re-render when the passcode is set.
  const hasPasscode = useLockStore((s) => s.passcode !== null)
  const unlockWithPasscode = useLockStore((s) => s.unlockWithPasscode)
  const unlockWithBiometrics = useLockStore((s) => s.unlockWithBiometrics)
  const setPasscode = useLockStore((s) => s.setPasscode)

  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(() => {
    const wait = getLockoutRemainingMs()
    return wait > 0 ? waitMessage(wait) : null
  })
  const [checking, setChecking] = useState(false)
  const [biometricsAvailable, setBiometricsAvailable] = useState(false)
  const [newCode, setNewCode] = useState('')

  useEffect(() => {
    void isWebAuthnAvailable().then((avail) => setBiometricsAvailable(avail && hasBiometricUnlock()))
  }, [])

  const wrap = (content: React.ReactElement) => (inline ? content : <div className="page">{content}</div>)

  if (!hasPasscode) {
    return wrap(
      <div className="card" style={{ maxWidth: 360 }}>
        <h2 style={{ marginTop: 0, fontSize: 16 }}>Set a passcode</h2>
        <p className="muted">Choose a 4 to 8 digit passcode to protect locked items.</p>
        <input
          className="input"
          type="password"
          inputMode="numeric"
          pattern="[0-9]*"
          aria-label="New passcode"
          value={newCode}
          onChange={(e) => setNewCode(e.target.value.replace(/\D/g, '').slice(0, 8))}
          style={{ marginBottom: 10 }}
        />
        <button className="btn btn-primary" disabled={newCode.length < 4} onClick={() => void setPasscode(newCode)}>
          Set passcode
        </button>
      </div>,
    )
  }

  const submit = async () => {
    if (checking || code.length < 4) return
    setChecking(true)
    try {
      const result = await unlockWithPasscode(code)
      if (!result.ok) {
        setError(result.retryAfterMs > 0 ? waitMessage(result.retryAfterMs) : 'Incorrect passcode.')
        setCode('')
      }
    } finally {
      setChecking(false)
    }
  }

  return wrap(
    <div className="card" style={{ maxWidth: 360 }}>
      <h2 style={{ marginTop: 0, fontSize: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
        <Icon name="lock" size={18} /> Locked
      </h2>
      <p className="muted">{message}</p>
      <input
        className="input"
        type="password"
        inputMode="numeric"
        pattern="[0-9]*"
        aria-label="Passcode"
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 8))}
        onKeyDown={(e) => e.key === 'Enter' && void submit()}
        style={{ marginBottom: 10 }}
        autoFocus
      />
      {error && (
        <p role="alert" style={{ color: 'var(--danger)', fontSize: 13 }}>
          {error}
        </p>
      )}
      <div style={{ display: 'flex', gap: 8 }}>
        <button className="btn btn-primary" onClick={() => void submit()} disabled={checking} style={{ flex: 1, justifyContent: 'center' }}>
          {checking ? 'Checking' : 'Unlock'}
        </button>
        {biometricsAvailable && (
          <button className="btn" onClick={() => void unlockWithBiometrics()} aria-label="Use device biometrics">
            <Icon name="fingerprint" size={18} />
          </button>
        )}
      </div>
    </div>,
  )
}
