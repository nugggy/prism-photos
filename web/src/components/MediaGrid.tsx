import { useVirtualizer } from '@tanstack/react-virtual'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { MediaItem } from '../plex/model'
import type { ServerRef } from '../plex/urls'
import { buildThumbUrl, pickThumbSize } from '../plex/urls'
import { groupByMonth } from '../plex/timeline'
import { formatDuration } from '../lib/format'
import { Icon } from './Icon'

type Row =
  | { type: 'header'; key: string; label: string }
  | { type: 'items'; key: string; items: MediaItem[]; startIndex: number }

export interface MediaGridProps {
  items: MediaItem[]
  server: ServerRef
  density: number
  onDensityChange: (density: number) => void
  selectedIds: Set<string>
  onToggleSelect: (item: MediaItem, index: number, opts: { shift: boolean; ctrl: boolean }) => void
  onOpen: (item: MediaItem, index: number) => void
  selectionMode: boolean
  /** Month headers only make sense when the items are in date order. Defaults to true. */
  groupByMonth?: boolean
}

function buildRows(items: MediaItem[], density: number, grouped: boolean): Row[] {
  const groups = grouped ? groupByMonth(items) : [{ key: 'all', label: '', items }]
  const out: Row[] = []
  let cursor = 0
  for (const group of groups) {
    if (grouped) out.push({ type: 'header', key: `h-${group.key}`, label: group.label })
    for (let i = 0; i < group.items.length; i += density) {
      const slice = group.items.slice(i, i + density)
      out.push({ type: 'items', key: `${group.key}-${i}`, items: slice, startIndex: cursor })
      cursor += slice.length
    }
  }
  return out
}

export function MediaGrid({
  items,
  server,
  density,
  onDensityChange,
  selectedIds,
  onToggleSelect,
  onOpen,
  selectionMode,
  groupByMonth: grouped = true,
}: MediaGridProps): React.ReactElement {
  const parentRef = useRef<HTMLDivElement>(null)
  const [containerWidth, setContainerWidth] = useState(800)
  const [focusedIndex, setFocusedIndex] = useState(0)
  const pendingFocus = useRef<number | null>(null)

  useEffect(() => {
    const el = parentRef.current
    if (!el || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width
      if (width) setContainerWidth(width)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  const rows = useMemo<Row[]>(() => buildRows(items, density, grouped), [items, density, grouped])

  // Where each item sits: its row in `rows` and its column, used for arrow key movement.
  const positions = useMemo(() => {
    const pos: { row: number; col: number }[] = []
    rows.forEach((r, rowIndex) => {
      if (r.type === 'items') r.items.forEach((_, col) => (pos[r.startIndex + col] = { row: rowIndex, col }))
    })
    return pos
  }, [rows])

  const gap = 4
  const cellSize = Math.max(60, (containerWidth - gap * (density - 1)) / density)
  const thumbSize = pickThumbSize(cellSize, typeof window !== 'undefined' ? window.devicePixelRatio : 1)

  const rowVirtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: (i) => (rows[i]?.type === 'header' ? 36 : cellSize + gap),
    overscan: 6,
    initialRect: { width: containerWidth, height: 800 },
  })

  // Row heights depend on the cell size, which changes with container width and density.
  useEffect(() => {
    rowVirtualizer.measure()
  }, [cellSize, density, rowVirtualizer])

  // Fast scroll month index (desktop only, per CSS).
  const monthIndex = useMemo(() => rows.filter((r) => r.type === 'header') as Extract<Row, { type: 'header' }>[], [rows])
  const scrollToMonth = useCallback(
    (key: string) => {
      const rowIndex = rows.findIndex((r) => r.key === key)
      if (rowIndex >= 0) rowVirtualizer.scrollToIndex(rowIndex, { align: 'start' })
    },
    [rows, rowVirtualizer],
  )

  // Ctrl+wheel to change density.
  useEffect(() => {
    const el = parentRef.current
    if (!el) return
    const handler = (e: WheelEvent) => {
      if (!e.ctrlKey) return
      e.preventDefault()
      onDensityChange(density + (e.deltaY > 0 ? 1 : -1))
    }
    el.addEventListener('wheel', handler, { passive: false })
    return () => el.removeEventListener('wheel', handler)
  }, [density, onDensityChange])

  // Basic pinch to zoom (touch) support.
  useEffect(() => {
    const el = parentRef.current
    if (!el) return
    let lastDist = 0
    const dist = (touches: TouchList) => {
      const [a, b] = [touches[0], touches[1]]
      return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
    }
    const onTouchStart = (e: TouchEvent) => {
      if (e.touches.length === 2) lastDist = dist(e.touches)
    }
    const onTouchMove = (e: TouchEvent) => {
      if (e.touches.length !== 2) return
      const d = dist(e.touches)
      if (lastDist > 0 && Math.abs(d - lastDist) > 20) {
        onDensityChange(density + (d > lastDist ? -1 : 1))
        lastDist = d
      }
    }
    el.addEventListener('touchstart', onTouchStart, { passive: true })
    el.addEventListener('touchmove', onTouchMove, { passive: true })
    return () => {
      el.removeEventListener('touchstart', onTouchStart)
      el.removeEventListener('touchmove', onTouchMove)
    }
  }, [density, onDensityChange])

  const activeIndex = Math.max(0, Math.min(focusedIndex, items.length - 1))

  /** Index of the item a key press moves to from `from`, or null if the key is not a grid key. */
  const targetFor = (from: number, key: string): number | null => {
    const last = items.length - 1
    const here = positions[from]
    if (!here) return null
    const sameColumnIn = (rowIndex: number): number => {
      const row = rows[rowIndex] as Extract<Row, { type: 'items' }>
      return row.startIndex + Math.min(here.col, row.items.length - 1)
    }
    switch (key) {
      case 'ArrowRight':
        return Math.min(last, from + 1)
      case 'ArrowLeft':
        return Math.max(0, from - 1)
      case 'ArrowDown': {
        for (let r = here.row + 1; r < rows.length; r++) if (rows[r].type === 'items') return sameColumnIn(r)
        return from
      }
      case 'ArrowUp': {
        for (let r = here.row - 1; r >= 0; r--) if (rows[r].type === 'items') return sameColumnIn(r)
        return from
      }
      case 'Home':
        return 0
      case 'End':
        return last
      default:
        return null
    }
  }

  // Keys are handled on the grid itself (not window) and only when a cell has focus, so form
  // controls elsewhere on the page keep their arrow keys. Enter and Space are left to the
  // focused button, which fires its own click.
  const onGridKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const target = e.target as HTMLElement
    if (target.dataset.index === undefined || e.altKey || e.ctrlKey || e.metaKey) return
    const next = targetFor(Number(target.dataset.index), e.key)
    if (next === null) return
    e.preventDefault()
    setFocusedIndex(next)
    pendingFocus.current = next
    const pos = positions[next]
    if (pos) rowVirtualizer.scrollToIndex(pos.row, { align: 'auto' })
  }

  // Moves DOM focus once the target cell has been rendered (it may need a scroll first).
  useEffect(() => {
    const index = pendingFocus.current
    if (index === null) return
    const el = parentRef.current?.querySelector<HTMLElement>(`[data-index="${index}"]`)
    if (el) {
      el.focus({ preventScroll: true })
      pendingFocus.current = null
    }
  })

  const virtualRows = rowVirtualizer.getVirtualItems()
  // Roving tabindex: exactly one cell is tabbable. If the active cell has been scrolled out of
  // the rendered window, the first rendered cell takes its place so Tab still reaches the grid.
  const renderedItemRows = virtualRows.map((v) => rows[v.index]).filter((r): r is Extract<Row, { type: 'items' }> => r?.type === 'items')
  const activeRendered = renderedItemRows.some((r) => activeIndex >= r.startIndex && activeIndex < r.startIndex + r.items.length)
  const tabbableIndex = activeRendered ? activeIndex : (renderedItemRows[0]?.startIndex ?? activeIndex)

  return (
    <div style={{ display: 'flex', gap: 8 }}>
      <div ref={parentRef} role="group" aria-label="Photos and videos" onKeyDown={onGridKeyDown} style={{ flex: 1, height: 'calc(100vh - 180px)', overflow: 'auto', position: 'relative' }}>
        <div style={{ height: rowVirtualizer.getTotalSize(), position: 'relative' }}>
          {virtualRows.map((virtualRow) => {
            const row = rows[virtualRow.index]
            return (
              <div
                key={row.key}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  transform: `translateY(${virtualRow.start}px)`,
                }}
              >
                {row.type === 'header' ? (
                  <div className="month-header">{row.label}</div>
                ) : (
                  <div style={{ display: 'flex', gap }}>
                    {row.items.map((item, i) => {
                      const globalIndex = row.startIndex + i
                      const selected = selectedIds.has(item.id)
                      return (
                        <button
                          key={item.id}
                          type="button"
                          data-index={globalIndex}
                          tabIndex={globalIndex === tabbableIndex ? 0 : -1}
                          aria-pressed={selectionMode || selected ? selected : undefined}
                          className={`grid-cell${selected ? ' selected' : ''}`}
                          style={{ width: cellSize, height: cellSize, border: 'none', padding: 0 }}
                          onFocus={() => setFocusedIndex(globalIndex)}
                          onClick={(e) => {
                            setFocusedIndex(globalIndex)
                            if (selectionMode || e.shiftKey || e.ctrlKey || e.metaKey) {
                              onToggleSelect(item, globalIndex, { shift: e.shiftKey, ctrl: e.ctrlKey || e.metaKey })
                            } else {
                              onOpen(item, globalIndex)
                            }
                          }}
                          aria-label={item.title}
                        >
                          <img src={buildThumbUrl(server, item.thumbPath, thumbSize, thumbSize)} loading="lazy" alt="" />
                          {(selectionMode || selected) && <span className="checkbox" />}
                          {item.favourite && (
                            <span className="badge badge-favourite">
                              <Icon name="heartFilled" size={12} />
                            </span>
                          )}
                          {item.kind === 'video' && (
                            <span className="badge badge-duration">
                              <Icon name="play" size={11} />
                              {formatDuration(item.durationMs)}
                            </span>
                          )}
                        </button>
                      )
                    })}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>
      {monthIndex.length > 0 && (
        <div className="scroll-index" aria-label="Jump to month">
          {monthIndex.map((m) => (
            <button key={m.key} onClick={() => scrollToMonth(m.key)} type="button">
              {m.label.split(' ')[0].slice(0, 3)}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
