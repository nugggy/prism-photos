import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryRouter, Outlet, RouterProvider, useLocation } from 'react-router-dom'
import { Viewer } from './Viewer'
import type { ViewerContext } from '../lib/viewerContext'
import { connectFakeServer, makeItem, resetLockStore } from '../test/fixtures'
import type { MediaItem } from '../plex/model'

function LocationProbe(): React.ReactElement {
  return <div data-testid="location">{useLocation().pathname}</div>
}

function renderViewer(items: MediaItem[], startId: string) {
  function ListPage(): React.ReactElement {
    const context: ViewerContext = { items, basePath: '/photos' }
    return (
      <>
        <LocationProbe />
        <Outlet context={context} />
      </>
    )
  }
  const router = createMemoryRouter([{ path: '/photos', element: <ListPage />, children: [{ path: 'view/:itemId', element: <Viewer /> }] }], {
    initialEntries: [`/photos/view/${startId}`],
  })
  render(<RouterProvider router={router} />)
}

const location = () => screen.getByTestId('location').textContent

describe('Viewer keyboard handling', () => {
  beforeEach(() => {
    localStorage.clear()
    connectFakeServer()
    resetLockStore()
  })

  it('steps through the list with the arrow keys', () => {
    renderViewer([makeItem('1'), makeItem('2'), makeItem('3')], '1')
    fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(location()).toBe('/photos/view/2')
    fireEvent.keyDown(window, { key: 'ArrowLeft' })
    expect(location()).toBe('/photos/view/1')
  })

  it('ignores arrows and Escape typed into the rename field', () => {
    renderViewer([makeItem('1'), makeItem('2')], '1')
    fireEvent.doubleClick(screen.getByText('Photo 1'))
    const input = screen.getByLabelText('Title')
    expect(input).toHaveFocus()
    fireEvent.keyDown(input, { key: 'ArrowRight' })
    fireEvent.keyDown(input, { key: 'ArrowLeft' })
    expect(location()).toBe('/photos/view/1')
    // Escape cancels the rename but keeps the viewer open.
    fireEvent.keyDown(input, { key: 'Escape' })
    expect(screen.queryByLabelText('Title')).not.toBeInTheDocument()
    expect(location()).toBe('/photos/view/1')
  })

  it('does not step when another handler already used the key', () => {
    renderViewer([makeItem('1'), makeItem('2')], '1')
    const event = new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true })
    event.preventDefault()
    act(() => {
      window.dispatchEvent(event)
    })
    expect(location()).toBe('/photos/view/1')
  })

  it('does not close on Escape while a dialog is open, and the dialog closes instead', () => {
    renderViewer([makeItem('1'), makeItem('2')], '1')
    const deleteButton = screen.getByLabelText('Delete')
    deleteButton.focus()
    fireEvent.click(deleteButton)
    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveAccessibleName('Delete item')
    // Destructive dialogs start on Cancel.
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
    fireEvent.keyDown(screen.getByRole('button', { name: 'Cancel' }), { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(location()).toBe('/photos/view/1')
    // Focus returns to the button that opened the dialog.
    expect(screen.getByLabelText('Delete')).toHaveFocus()
  })

  it('leaves arrow keys to the video player on a video', () => {
    renderViewer([makeItem('1', { kind: 'video', durationMs: 60_000 }), makeItem('2')], '1')
    fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(location()).toBe('/photos/view/1')
  })

  it('shows an Edit button for photos only', () => {
    renderViewer([makeItem('1'), makeItem('2', { kind: 'video', durationMs: 1000 })], '1')
    expect(screen.getByLabelText('Edit')).toBeInTheDocument()
    fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(screen.queryByLabelText('Edit')).not.toBeInTheDocument()
  })
})
