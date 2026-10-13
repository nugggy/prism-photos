import { useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useLockStore } from '../state/lockStore'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useTimeline } from '../lib/useTimeline'
import { useItemList } from '../lib/useItemList'
import { viewerPath, type ViewerContext } from '../lib/viewerContext'
import { fetchAlbumMeta } from '../plex/api'
import { buildThumbUrl } from '../plex/urls'
import type { Album, MediaItem } from '../plex/model'
import { PasscodeGate } from '../components/PasscodeGate'
import { MediaGrid } from '../components/MediaGrid'
import { Icon } from '../components/Icon'

const BASE_PATH = '/locked'

export function Locked(): React.ReactElement {
  const unlocked = useLockStore((s) => s.unlocked)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const lockedAlbumIds = useLockStore((s) => s.lockedAlbumIds)
  const lock = useLockStore((s) => s.lock)
  const unlockItems = useLockStore((s) => s.unlockItems)
  const unlockAlbum = useLockStore((s) => s.unlockAlbum)
  const { items } = useTimeline()
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const navigate = useNavigate()

  const [albums, setAlbums] = useState<Record<string, Album | null>>({})

  const lockedItems = useMemo(() => items.filter((i) => lockedItemIds.includes(i.id)), [items, lockedItemIds])
  const list = useItemList(lockedItems, { hideLocked: false })
  const visibleItems = list.visible
  const selection = useSelection(visibleItems)

  const viewerContext = useMemo<ViewerContext>(
    () => ({ items: visibleItems, basePath: BASE_PATH, onItemChanged: (id, patch) => list.patch([id], patch), onItemsRemoved: list.remove }),
    [visibleItems, list],
  )

  // Locked albums only exist as ids in the lock store, so look up their titles and covers.
  useEffect(() => {
    if (!unlocked || !server || !sectionKey) return
    const missing = lockedAlbumIds.filter((id) => !(id in albums))
    if (missing.length === 0) return
    let cancelled = false
    void Promise.all(missing.map((id) => fetchAlbumMeta(server, sectionKey, id).catch(() => null))).then((metas) => {
      if (cancelled) return
      setAlbums((prev) => {
        const next = { ...prev }
        missing.forEach((id, i) => (next[id] = metas[i]))
        return next
      })
    })
    return () => {
      cancelled = true
    }
  }, [unlocked, server, sectionKey, lockedAlbumIds, albums])

  if (!unlocked) return <PasscodeGate />
  if (!server) return <div className="page">Not connected.</div>

  const openViewer = (item: MediaItem) => navigate(viewerPath(BASE_PATH, item.id))

  const handleUnlockSelected = () => {
    unlockItems(Array.from(selection.selectedIds))
    selection.clear()
  }

  const nothingLocked = lockedItemIds.length === 0 && lockedAlbumIds.length === 0

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Locked</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          {selection.selectedIds.size > 0 && (
            <button className="btn" onClick={handleUnlockSelected}>
              <Icon name="unlock" size={16} /> Unlock {selection.selectedIds.size}
            </button>
          )}
          <button className="btn" onClick={lock}>
            Lock now
          </button>
        </div>
      </div>
      <p className="muted" style={{ fontSize: 13, marginTop: 0 }}>
        Locked items and albums are hidden on this device only. They remain visible in other Plex apps.
      </p>
      {nothingLocked && <p className="muted">Nothing locked yet. Lock items from Photos or the viewer, or lock an album from its page.</p>}

      {lockedAlbumIds.length > 0 && (
        <>
          <h2 style={{ fontSize: 16 }}>Albums</h2>
          <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', marginBottom: 24 }}>
            {lockedAlbumIds.map((id) => {
              const album = albums[id]
              return (
                <div key={id}>
                  <Link to={`/albums/${id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
                    <div className="grid-cell" style={{ aspectRatio: '1' }}>
                      {album?.thumbPath && <img src={buildThumbUrl(server, album.thumbPath, 480, 480)} alt="" loading="lazy" />}
                    </div>
                    <div style={{ marginTop: 6, fontSize: 13 }}>
                      <strong>{album ? album.title : album === null ? 'Album not found' : 'Loading'}</strong>
                    </div>
                  </Link>
                  <button className="btn" style={{ marginTop: 6 }} onClick={() => unlockAlbum(id)} aria-label={`Unlock album ${album?.title ?? id}`}>
                    <Icon name="unlock" size={14} /> Unlock
                  </button>
                </div>
              )
            })}
          </div>
        </>
      )}

      {visibleItems.length > 0 && (
        <>
          {lockedAlbumIds.length > 0 && <h2 style={{ fontSize: 16 }}>Items</h2>}
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
        </>
      )}

      <Outlet context={viewerContext} />
    </div>
  )
}
