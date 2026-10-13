import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { Settings } from './Settings'
import { useLockStore } from '../state/lockStore'
import { createPasscodeRecord } from '../lib/passcode'
import { connectFakeServer, resetLockStore } from '../test/fixtures'

function renderSettings() {
  render(
    <MemoryRouter>
      <Settings />
    </MemoryRouter>,
  )
}

describe('Settings lock section', () => {
  beforeEach(() => {
    localStorage.clear()
    connectFakeServer()
    resetLockStore()
  })

  it('switches from the set form to the passcode controls once a passcode is set', async () => {
    renderSettings()
    expect(screen.getByText(/hides items on this device only/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('New passcode'), { target: { value: '1234' } })
    fireEvent.click(screen.getByRole('button', { name: 'Set' }))
    // Re-renders because the component selects the passcode, not a stable helper function.
    expect(await screen.findByRole('button', { name: 'Remove passcode' })).toBeInTheDocument()
    expect(screen.queryByLabelText('New passcode')).not.toBeInTheDocument()
  })

  it('asks for the passcode, then for confirmation, before removing it', async () => {
    useLockStore.setState({ passcode: await createPasscodeRecord('1234', 1000), lockedItemIds: ['a', 'b'] })
    renderSettings()
    expect(screen.queryByRole('button', { name: 'Remove passcode' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Unlock to change lock settings/ }))
    fireEvent.change(screen.getByLabelText('Passcode'), { target: { value: '1234' } })
    fireEvent.click(screen.getByRole('button', { name: 'Unlock' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Remove passcode' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('2 locked items will become visible again')
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect(useLockStore.getState().passcode).toBeNull()
    expect(useLockStore.getState().lockedItemIds).toEqual([])
    expect(await screen.findByLabelText('New passcode')).toBeInTheDocument()
  })
})
