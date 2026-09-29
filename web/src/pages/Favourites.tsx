import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useViewerListStore } from '../state/viewerListStore'
import { fetchFavourites, deleteItem, rateItem } from '../plex/api'
import { buildDownloadUrl } from '../plex/urls'
import type { MediaItem } from '../plex/model'
import { filterLocked } from '../plex/timeline'
import { MediaGrid } from '../components/MediaGrid'
import { SelectionBar } from '../components/SelectionBar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ShareSheet } from '../components/ShareSheet'
import { AddToAlbumDialog } from '../components/AddToAlbumDialog'
import { downloadUrl } from '../lib/download'

export function Favourites(): React.ReactElement {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const lockItem = useLockStore((s) => s.lockItem)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const setViewerList = useViewerListStore((s) => s.setList)
  const navigate = useNavigate()

  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [showAddToAlbum, setShowAddToAlbum] = useState(false)
  const [busy, setBusy] = useState(false)

  const [reloadToken, setReloadToken] = useState(0)
  const reload = () => setReloadToken((t) => t + 1)

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [server, sectionKey, reloadToken])

  const visibleItems = useMemo(() => filterLocked(items, new Set(lockedItemIds)), [items, lockedItemIds])
  const selection = useSelection(visibleItems)

  if (!server) return <div className="page">Not connected.</div>

  const openViewer = (_item: MediaItem, index: number) => {
    setViewerList('favourites', visibleItems)
    navigate(`/view/favourites/${index}`)
  }

  const selectedItems = visibleItems.filter((i) => selection.selectedIds.has(i.id))

  const handleFavourite = async () => {
    setBusy(true)
    try {
      // Unfavouriting from this page removes the item from the list, so refresh afterwards.
      await Promise.all(selectedItems.map((i) => rateItem(server, i.id, false)))
      reload()
    } finally {
      setBusy(false)
      selection.clear()
    }
  }

  const handleLock = () => {
    selectedItems.forEach((i) => lockItem(i.id))
    selection.clear()
  }

  const handleDownload = () => {
    selectedItems.forEach((i) => downloadUrl(buildDownloadUrl(server, i.partKey), i.title))
    selection.clear()
  }

  const handleDelete = async () => {
    setBusy(true)
    try {
      await Promise.all(selectedItems.map((i) => deleteItem(server, i.id)))
      reload()
    } finally {
      setBusy(false)
      setConfirmDelete(false)
      selection.clear()
    }
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

      <SelectionBar
        count={selection.selectedIds.size}
        onAddToAlbum={() => setShowAddToAlbum(true)}
        onShare={() => setShowShare(true)}
        onDownload={handleDownload}
        onFavourite={() => void handleFavourite()}
        onLock={handleLock}
        onDelete={() => setConfirmDelete(true)}
        onClear={selection.clear}
      />

      {showAddToAlbum && (
        <AddToAlbumDialog
          itemIds={selectedItems.map((i) => i.id)}
          onClose={() => setShowAddToAlbum(false)}
          onAdded={() => selection.clear()}
        />
      )}

      {confirmDelete && (
        <ConfirmDialog
          title="Delete selected"
          message={`This deletes ${selectedItems.length} item(s) from Plex permanently. This cannot be undone.`}
          confirmLabel={busy ? 'Deleting' : 'Delete'}
          danger
          onConfirm={() => void handleDelete()}
          onCancel={() => setConfirmDelete(false)}
        />
      )}

      {showShare && selectedItems.length === 1 && (
        <ShareSheet
          title={selectedItems[0].title}
          link={`${window.location.origin}${window.location.pathname}#/view/favourites/0`}
          fileUrl={buildDownloadUrl(server, selectedItems[0].partKey)}
          fileName={selectedItems[0].title}
          onClose={() => setShowShare(false)}
        />
      )}
    </div>
  )
}
