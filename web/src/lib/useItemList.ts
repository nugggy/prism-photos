import { useCallback, useMemo, useState } from 'react'
import type { MediaItem } from '../plex/model'
import { filterLocked } from '../plex/timeline'
import { useLockStore } from '../state/lockStore'

export interface ItemList {
  /** Items with local edits applied, removed items dropped and locked items hidden. */
  visible: MediaItem[]
  patch: (ids: string[], patch: Partial<MediaItem>) => void
  remove: (ids: string[]) => void
}

/**
 * Local overlay on a list loaded from Plex: favourite/rename edits and deletions show straight
 * away without a reload, and locked items (or items in locked albums) are filtered out.
 */
export function useItemList(items: MediaItem[], options: { hideLocked?: boolean; openAlbumId?: string } = {}): ItemList {
  const hideLocked = options.hideLocked ?? true
  // Inside a locked album that has been unlocked, its own items are shown.
  const openAlbumId = options.openAlbumId
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const lockedAlbumIds = useLockStore((s) => s.lockedAlbumIds)
  const [overrides, setOverrides] = useState<Record<string, Partial<MediaItem>>>({})
  const [removed, setRemoved] = useState<ReadonlySet<string>>(() => new Set())

  const visible = useMemo(() => {
    const merged = items.filter((it) => !removed.has(it.id)).map((it) => (overrides[it.id] ? { ...it, ...overrides[it.id] } : it))
    if (!hideLocked) return merged
    return filterLocked(merged, new Set(lockedItemIds), new Set(lockedAlbumIds.filter((id) => id !== openAlbumId)))
  }, [items, overrides, removed, hideLocked, lockedItemIds, lockedAlbumIds, openAlbumId])

  const patch = useCallback((ids: string[], p: Partial<MediaItem>) => {
    setOverrides((prev) => {
      const next = { ...prev }
      ids.forEach((id) => (next[id] = { ...next[id], ...p }))
      return next
    })
  }, [])

  const remove = useCallback((ids: string[]) => {
    setRemoved((prev) => new Set([...prev, ...ids]))
  }, [])

  return useMemo(() => ({ visible, patch, remove }), [visible, patch, remove])
}
