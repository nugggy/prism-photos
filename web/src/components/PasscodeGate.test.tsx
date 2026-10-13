import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { PasscodeGate } from './PasscodeGate'
import { useLockStore } from '../state/lockStore'
import { createPasscodeRecord, FREE_ATTEMPTS } from '../lib/passcode'
import { resetLockStore } from '../test/fixtures'

function Protected(): React.ReactElement {
  const unlocked = useLockStore((s) => s.unlocked)
  return unlocked ? <p>Secret content</p> : <PasscodeGate />
}

async function enter(code: string) {
  const input = screen.getByLabelText('Passcode')
  fireEvent.change(input, { target: { value: code } })
  fireEvent.click(screen.getByRole('button', { name: 'Unlock' }))
}

describe('PasscodeGate', () => {
  beforeEach(async () => {
    localStorage.clear()
    resetLockStore()
  })

  it('offers to set a passcode when none exists, and switches once one is set', async () => {
    render(<PasscodeGate />)
    expect(screen.getByText('Set a passcode')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('New passcode'), { target: { value: '4321' } })
    fireEvent.click(screen.getByRole('button', { name: 'Set passcode' }))
    // The gate selects the passcode itself (not a helper function), so it re-renders.
    expect(await screen.findByLabelText('Passcode')).toBeInTheDocument()
  })

  it('rejects a wrong passcode and unlocks with the right one', async () => {
    useLockStore.setState({ passcode: await createPasscodeRecord('1234', 1000) })
    render(<Protected />)
    await enter('9999')
    expect(await screen.findByText('Incorrect passcode.')).toBeInTheDocument()
    await enter('1234')
    expect(await screen.findByText('Secret content')).toBeInTheDocument()
  })

  it('makes the user wait after repeated wrong entries', async () => {
    useLockStore.setState({ passcode: await createPasscodeRecord('1234', 1000) })
    render(<Protected />)
    for (let i = 0; i < FREE_ATTEMPTS; i++) {
      await enter('0000')
      await screen.findByRole('alert')
    }
    expect(await screen.findByText(/Too many attempts/)).toBeInTheDocument()
    // Even the right passcode is refused until the wait is over.
    await enter('1234')
    expect(await screen.findByText(/Too many attempts/)).toBeInTheDocument()
    expect(useLockStore.getState().unlocked).toBe(false)
  })
})
