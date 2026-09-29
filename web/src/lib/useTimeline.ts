import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchTimeline } from '../plex/api'
import type { MediaItem } from '../plex/model'
import { useServer } from './useServer'
import { useSessionStore } from '../state/sessionStore'
import { getCachedTimeline, setCachedTimeline } from './cache'

export interface UseTimelineResult {
  items: MediaItem[]
  loading: boolean
  refreshing: boolean
  error: string | null
  refresh: () => void
}

/** Loads the cached timeline instantly, then refreshes from the server in the background. */
export function useTimeline(): UseTimelineResult {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestId = useRef(0)

  const load = useCallback(
    async (background: boolean) => {
      if (!server || !sectionKey) {
        setLoading(false)
        return
      }
      const myId = ++requestId.current
      if (background) setRefreshing(true)
      else setLoading(true)
      try {
        const fresh = await fetchTimeline(server, sectionKey)
        if (requestId.current !== myId) return
        setItems(fresh)
        setError(null)
        void setCachedTimeline(sectionKey, fresh)
      } catch (e) {
        if (requestId.current === myId) {
          setError(e instanceof Error ? e.message : 'Failed to load photos.')
        }
      } finally {
        if (requestId.current === myId) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    },
    [server, sectionKey],
  )

  useEffect(() => {
    let cancelled = false
    if (!sectionKey) {
      setLoading(false)
      return
    }
    setLoading(true)
    void getCachedTimeline(sectionKey).then((cached) => {
      if (cancelled) return
      if (cached && cached.length > 0) {
        setItems(cached)
        setLoading(false)
        void load(true)
      } else {
        void load(false)
      }
    })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sectionKey, server?.baseUrl])

  const refresh = useCallback(() => void load(false), [load])

  return { items, loading, refreshing, error, refresh }
}
