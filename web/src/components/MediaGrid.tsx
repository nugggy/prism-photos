import { useVirtualizer } from '@tanstack/react-virtual'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { MediaItem } from '../plex/model'
import type { ServerRef } from '../plex/urls'
import { buildThumbUrl } from '../plex/urls'
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
}: MediaGridProps): React.ReactElement {
  const parentRef = useRef<HTMLDivElement>(null)
  const [containerWidth, setContainerWidth] = useState(800)
  const [focusedIndex, setFocusedIndex] = useState(0)

  useEffect(() => {
    const el = parentRef.current
    if (!el) return
    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width
      if (width) setContainerWidth(width)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  const rows = useMemo<Row[]>(() => {
    const groups = groupByMonth(items)
    const out: Row[] = []
    let cursor = 0
    for (const group of groups) {
      out.push({ type: 'header', key: `h-${group.key}`, label: group.label })
      for (let i = 0; i < group.items.length; i += density) {
        const slice = group.items.slice(i, i + density)
        out.push({ type: 'items', key: `${group.key}-${i}`, items: slice, startIndex: cursor })
        cursor += slice.length
      }
    }
    return out
  }, [items, density])

  const gap = 4
  const cellSize = Math.max(60, (containerWidth - gap * (density - 1)) / density)

  const rowVirtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: (i) => (rows[i]?.type === 'header' ? 36 : cellSize + gap),
    overscan: 6,
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

  // Keyboard navigation.
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (items.length === 0) return
      const target = e.target as HTMLElement
      if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return
      if (e.key === 'ArrowRight') {
        setFocusedIndex((i) => Math.min(items.length - 1, i + 1))
        e.preventDefault()
      } else if (e.key === 'ArrowLeft') {
        setFocusedIndex((i) => Math.max(0, i - 1))
        e.preventDefault()
      } else if (e.key === 'ArrowDown') {
        setFocusedIndex((i) => Math.min(items.length - 1, i + density))
        e.preventDefault()
      } else if (e.key === 'ArrowUp') {
        setFocusedIndex((i) => Math.max(0, i - density))
        e.preventDefault()
      } else if (e.key === 'Enter') {
        const item = items[focusedIndex]
        if (item) onOpen(item, focusedIndex)
      } else if (e.key === 'Escape') {
        // Handled by parent (clears selection).
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [items, focusedIndex, density, onOpen])

  return (
    <div style={{ display: 'flex', gap: 8 }}>
      <div ref={parentRef} style={{ flex: 1, height: 'calc(100vh - 180px)', overflow: 'auto', position: 'relative' }}>
        <div style={{ height: rowVirtualizer.getTotalSize(), position: 'relative' }}>
          {rowVirtualizer.getVirtualItems().map((virtualRow) => {
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
                      const focused = globalIndex === focusedIndex
                      return (
                        <button
                          key={item.id}
                          className={`grid-cell${selected ? ' selected' : ''}`}
                          style={{
                            width: cellSize,
                            height: cellSize,
                            border: focused ? '2px solid var(--gold)' : 'none',
                            padding: 0,
                          }}
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
                          <img src={buildThumbUrl(server, item.thumbPath, 400, 400)} loading="lazy" alt="" />
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
      <div className="scroll-index" aria-label="Jump to month">
        {monthIndex.map((m) => (
          <button key={m.key} onClick={() => scrollToMonth(m.key)} type="button">
            {m.label.split(' ')[0].slice(0, 3)}
          </button>
        ))}
      </div>
    </div>
  )
}
