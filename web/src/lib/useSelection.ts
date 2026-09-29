import { useCallback, useEffect, useRef, useState } from 'react'
import type { MediaItem } from '../plex/model'

export interface UseSelectionResult {
  selectedIds: Set<string>
  selectionMode: boolean
  setSelectionMode: (on: boolean) => void
  toggle: (item: MediaItem, index: number, opts: { shift: boolean; ctrl: boolean }) => void
  clear: () => void
  selectAll: () => void
}

/** Multi-select state: click toggles, shift-click selects a range, ctrl/cmd-click toggles one. */
export function useSelection(items: MediaItem[]): UseSelectionResult {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [selectionMode, setSelectionMode] = useState(false)
  const lastIndexRef = useRef<number | null>(null)
  const itemsRef = useRef<MediaItem[]>(items)

  useEffect(() => {
    itemsRef.current = items
  }, [items])

  const toggle = useCallback((item: MediaItem, index: number, opts: { shift: boolean; ctrl: boolean }) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (opts.shift && lastIndexRef.current !== null) {
        const [from, to] = [lastIndexRef.current, index].sort((a, b) => a - b)
        for (let i = from; i <= to; i++) {
          const it = itemsRef.current[i]
          if (it) next.add(it.id)
        }
      } else {
        if (next.has(item.id)) next.delete(item.id)
        else next.add(item.id)
      }
      return next
    })
    lastIndexRef.current = index
    setSelectionMode(true)
  }, [])

  const clear = useCallback(() => {
    setSelectedIds(new Set())
    setSelectionMode(false)
    lastIndexRef.current = null
  }, [])

  const selectAll = useCallback(() => {
    setSelectedIds(new Set(itemsRef.current.map((i) => i.id)))
    setSelectionMode(true)
  }, [])

  return { selectedIds, selectionMode, setSelectionMode, toggle, clear, selectAll }
}
