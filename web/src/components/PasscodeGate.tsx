import { useEffect, useState } from 'react'
import { useLockStore } from '../state/lockStore'
import { hasBiometricUnlock, isWebAuthnAvailable } from '../lib/webauthn'
import { Icon } from './Icon'

export function PasscodeGate(): React.ReactElement {
  const hasPasscode = useLockStore((s) => s.hasPasscode)
  const unlockWithPasscode = useLockStore((s) => s.unlockWithPasscode)
  const unlockWithBiometrics = useLockStore((s) => s.unlockWithBiometrics)
  const setPasscode = useLockStore((s) => s.setPasscode)

  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [biometricsAvailable, setBiometricsAvailable] = useState(false)
  const [newCode, setNewCode] = useState('')

  useEffect(() => {
    void isWebAuthnAvailable().then((avail) => setBiometricsAvailable(avail && hasBiometricUnlock()))
  }, [])

  if (!hasPasscode()) {
    return (
      <div className="page">
        <div className="card" style={{ maxWidth: 360 }}>
          <h2 style={{ marginTop: 0, fontSize: 16 }}>Set a passcode</h2>
          <p className="muted">Choose a 4 to 8 digit passcode to protect locked items.</p>
          <input
            className="input"
            type="password"
            inputMode="numeric"
            pattern="[0-9]*"
            value={newCode}
            onChange={(e) => setNewCode(e.target.value.replace(/\D/g, '').slice(0, 8))}
            style={{ marginBottom: 10 }}
          />
          <button
            className="btn btn-primary"
            disabled={newCode.length < 4}
            onClick={() => void setPasscode(newCode)}
          >
            Set passcode
          </button>
        </div>
      </div>
    )
  }

  const submit = async () => {
    const ok = await unlockWithPasscode(code)
    if (!ok) {
      setError('Incorrect passcode.')
      setCode('')
    }
  }

  return (
    <div className="page">
      <div className="card" style={{ maxWidth: 360 }}>
        <h2 style={{ marginTop: 0, fontSize: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="lock" size={18} /> Locked
        </h2>
        <p className="muted">Enter your passcode to view locked items.</p>
        <input
          className="input"
          type="password"
          inputMode="numeric"
          pattern="[0-9]*"
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 8))}
          onKeyDown={(e) => e.key === 'Enter' && void submit()}
          style={{ marginBottom: 10 }}
          autoFocus
        />
        {error && <p style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</p>}
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-primary" onClick={() => void submit()} style={{ flex: 1, justifyContent: 'center' }}>
            Unlock
          </button>
          {biometricsAvailable && (
            <button className="btn" onClick={() => void unlockWithBiometrics()} aria-label="Use device biometrics">
              <Icon name="fingerprint" size={18} />
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
