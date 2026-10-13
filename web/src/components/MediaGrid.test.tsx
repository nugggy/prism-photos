import { fireEvent, render, screen } from '@testing-library/react'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { MediaGrid } from './MediaGrid'
import { makeItem } from '../test/fixtures'

const server = { baseUrl: 'http://plex.test:32400', token: 't' }
const items = Array.from({ length: 10 }, (_, i) => makeItem(String(i + 1)))

function renderGrid(onOpen = vi.fn()) {
  render(
    <>
      <select aria-label="Outside select">
        <option>A</option>
        <option>B</option>
      </select>
      <MediaGrid items={items} server={server} density={4} onDensityChange={() => {}} selectedIds={new Set()} onToggleSelect={() => {}} onOpen={onOpen} selectionMode={false} groupByMonth={false} />
    </>,
  )
  return onOpen
}

const cell = (title: string) => screen.getByRole('button', { name: title })
const tabbable = () => screen.getAllByRole('button').filter((b) => b.getAttribute('data-index') !== null && b.tabIndex === 0)

describe('MediaGrid keyboard navigation', () => {
  // jsdom has no layout, so give elements a size for the virtualiser to work with.
  const originals = {
    offsetHeight: Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight'),
    offsetWidth: Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth'),
  }
  beforeAll(() => {
    Object.defineProperty(HTMLElement.prototype, 'offsetHeight', { configurable: true, get: () => 800 })
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, get: () => 800 })
  })
  afterAll(() => {
    if (originals.offsetHeight) Object.defineProperty(HTMLElement.prototype, 'offsetHeight', originals.offsetHeight)
    if (originals.offsetWidth) Object.defineProperty(HTMLElement.prototype, 'offsetWidth', originals.offsetWidth)
  })

  it('makes exactly one cell tabbable', () => {
    renderGrid()
    expect(tabbable()).toHaveLength(1)
    expect(tabbable()[0]).toBe(cell('Photo 1'))
  })

  it('moves focus and the tab stop with the arrow keys', () => {
    renderGrid()
    cell('Photo 1').focus()
    fireEvent.keyDown(cell('Photo 1'), { key: 'ArrowRight' })
    expect(cell('Photo 2')).toHaveFocus()
    expect(tabbable()).toEqual([cell('Photo 2')])
    // Down keeps the column: index 1 + 4 columns = index 5 (Photo 6).
    fireEvent.keyDown(cell('Photo 2'), { key: 'ArrowDown' })
    expect(cell('Photo 6')).toHaveFocus()
    fireEvent.keyDown(cell('Photo 6'), { key: 'End' })
    expect(cell('Photo 10')).toHaveFocus()
    fireEvent.keyDown(cell('Photo 10'), { key: 'Home' })
    expect(cell('Photo 1')).toHaveFocus()
  })

  it('leaves Enter to the focused button so the item opens once', () => {
    const onOpen = renderGrid()
    cell('Photo 1').focus()
    fireEvent.keyDown(cell('Photo 1'), { key: 'Enter' })
    expect(onOpen).not.toHaveBeenCalled()
    fireEvent.click(cell('Photo 1'))
    expect(onOpen).toHaveBeenCalledTimes(1)
  })

  it('does not take arrow keys from form controls elsewhere on the page', () => {
    renderGrid()
    const select = screen.getByLabelText('Outside select')
    select.focus()
    fireEvent.keyDown(select, { key: 'ArrowDown' })
    expect(select).toHaveFocus()
    expect(tabbable()).toEqual([cell('Photo 1')])
  })
})
