import { useEffect, useMemo, useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useItemList } from '../lib/useItemList'
import { downloadItems, lockItemsOrExplain, reportBulkFailure, runForEach } from '../lib/bulk'
import { appItemLink, viewerPath, type ViewerContext } from '../lib/viewerContext'
import { fetchFavourites, deleteItem, rateItem } from '../plex/api'
import { buildDownloadUrl } from '../plex/urls'
import type { MediaItem } from '../plex/model'
import { MediaGrid } from '../components/MediaGrid'
import { SelectionBar } from '../components/SelectionBar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ShareSheet } from '../components/ShareSheet'
import { AddToAlbumDialog } from '../components/AddToAlbumDialog'

const BASE_PATH = '/favourites'

export function Favourites(): React.ReactElement {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
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

  const list = useItemList(items)
  const visibleItems = list.visible
  const selection = useSelection(visibleItems)

  const viewerContext = useMemo<ViewerContext>(
    () => ({ items: visibleItems, basePath: BASE_PATH, onItemChanged: (id, patch) => list.patch([id], patch), onItemsRemoved: list.remove }),
    [visibleItems, list],
  )

  if (!server) return <div className="page">Not connected.</div>

  const openViewer = (item: MediaItem) => navigate(viewerPath(BASE_PATH, item.id))

  const selectedItems = visibleItems.filter((i) => selection.selectedIds.has(i.id))

  const handleFavourite = async () => {
    setBusy(true)
    try {
      // Unfavouriting from this page removes the item from the list, so refresh afterwards.
      const result = await runForEach(
        selectedItems.map((i) => i.id),
        (id) => rateItem(server, id, false),
      )
      reportBulkFailure(result, 'unfavourite')
      reload()
    } finally {
      setBusy(false)
      selection.clear()
    }
  }

  const handleLock = () => {
    if (lockItemsOrExplain(selectedItems.map((i) => i.id))) selection.clear()
  }

  const handleDownload = () => {
    downloadItems(server, selectedItems)
    selection.clear()
  }

  const handleDelete = async () => {
    setBusy(true)
    try {
      const result = await runForEach(
        selectedItems.map((i) => i.id),
        (id) => deleteItem(server, id),
      )
      list.remove(result.succeeded)
      reportBulkFailure(result, 'delete')
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
          link={appItemLink(BASE_PATH, selectedItems[0].id)}
          fileUrl={buildDownloadUrl(server, selectedItems[0].partKey)}
          fileName={selectedItems[0].title}
          onClose={() => setShowShare(false)}
        />
      )}

      <Outlet context={viewerContext} />
    </div>
  )
}
