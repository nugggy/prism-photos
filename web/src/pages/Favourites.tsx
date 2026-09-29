import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useViewerListStore } from '../state/viewerListStore'
import { fetchFavourites } from '../plex/api'
import type { MediaItem } from '../plex/model'
import { filterLocked } from '../plex/timeline'
import { MediaGrid } from '../components/MediaGrid'

export function Favourites(): React.ReactElement {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const setViewerList = useViewerListStore((s) => s.setList)
  const navigate = useNavigate()

  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!server || !sectionKey) return
    let cancelled = false
    setLoading(true)
    fetchFavourites(server, sectionKey)
      .then((data) => !cancelled && setItems(data))
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Failed to load favourites.'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [server, sectionKey])

  const visibleItems = useMemo(() => filterLocked(items, new Set(lockedItemIds)), [items, lockedItemIds])
  const selection = useSelection(visibleItems)

  if (!server) return <div className="page">Not connected.</div>

  const openViewer = (_item: MediaItem, index: number) => {
    setViewerList('favourites', visibleItems)
    navigate(`/view/favourites/${index}`)
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Favourites</h1>
      </div>
      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loading && <div className="spinner" />}
      {!loading && visibleItems.length === 0 && <p className="muted">Nothing favourited yet.</p>}
      {visibleItems.length > 0 && (
        <MediaGrid
          items={visibleItems}
          server={server}
          density={density}
          onDensityChange={setDensity}
          selectedIds={selection.selectedIds}
          onToggleSelect={selection.toggle}
          onOpen={openViewer}
          selectionMode={selection.selectionMode}
        />
      )}
    </div>
  )
}
