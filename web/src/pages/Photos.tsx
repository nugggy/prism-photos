import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTimeline } from '../lib/useTimeline'
import { useServer } from '../lib/useServer'
import { useSelection } from '../lib/useSelection'
import { MediaGrid } from '../components/MediaGrid'
import { SelectionBar } from '../components/SelectionBar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ShareSheet } from '../components/ShareSheet'
import { useLockStore } from '../state/lockStore'
import { useSettingsStore } from '../state/settingsStore'
import { useViewerListStore } from '../state/viewerListStore'
import { filterLocked } from '../plex/timeline'
import { deleteItem, rateItem } from '../plex/api'
import { buildDownloadUrl } from '../plex/urls'
import type { MediaItem } from '../plex/model'
import { downloadUrl } from '../lib/download'

export function Photos(): React.ReactElement {
  const { items, loading, refreshing, error, refresh } = useTimeline()
  const server = useServer()
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const lockItem = useLockStore((s) => s.lockItem)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const setViewerList = useViewerListStore((s) => s.setList)
  const navigate = useNavigate()

  const [overrides, setOverrides] = useState<Record<string, Partial<MediaItem>>>({})
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [busy, setBusy] = useState(false)

  const visibleItems = useMemo(() => {
    const withOverrides = items.map((it) => (overrides[it.id] ? { ...it, ...overrides[it.id] } : it))
    return filterLocked(withOverrides, new Set(lockedItemIds))
  }, [items, overrides, lockedItemIds])

  const selection = useSelection(visibleItems)

  if (!server) return <div className="page">Not connected.</div>

  const openViewer = (_item: MediaItem, index: number) => {
    setViewerList('photos', visibleItems)
    navigate(`/view/photos/${index}`)
  }

  const selectedItems = visibleItems.filter((i) => selection.selectedIds.has(i.id))

  const handleFavourite = async () => {
    setBusy(true)
    const makeFav = !selectedItems.every((i) => i.favourite)
    try {
      await Promise.all(selectedItems.map((i) => rateItem(server, i.id, makeFav)))
      setOverrides((prev) => {
        const next = { ...prev }
        selectedItems.forEach((i) => (next[i.id] = { ...next[i.id], favourite: makeFav }))
        return next
      })
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
      refresh()
    } finally {
      setBusy(false)
      setConfirmDelete(false)
      selection.clear()
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Photos</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {refreshing && <span className="muted" style={{ fontSize: 12 }}>Refreshing…</span>}
          <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }} className="muted">
            Density
            <input type="range" min={2} max={8} value={density} onChange={(e) => setDensity(Number(e.target.value))} />
          </label>
          <button className="btn" onClick={refresh}>
            Refresh
          </button>
        </div>
      </div>

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loading && <div className="spinner" />}
      {!loading && visibleItems.length === 0 && <p className="muted">No photos or videos yet.</p>}

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
        onShare={() => setShowShare(true)}
        onDownload={handleDownload}
        onFavourite={() => void handleFavourite()}
        onLock={handleLock}
        onDelete={() => setConfirmDelete(true)}
        onClear={selection.clear}
      />

      {confirmDelete && (
        <ConfirmDialog
          title="Delete selected"
          message={`This deletes ${selectedItems.length} item(s) from Plex permanently. This cannot be undone.`}
          confirmLabel={busy ? 'Deleting…' : 'Delete'}
          danger
          onConfirm={() => void handleDelete()}
          onCancel={() => setConfirmDelete(false)}
        />
      )}

      {showShare && selectedItems.length === 1 && server && (
        <ShareSheet
          title={selectedItems[0].title}
          link={`${window.location.origin}${window.location.pathname}#/view/photos/0`}
          fileUrl={buildDownloadUrl(server, selectedItems[0].partKey)}
          fileName={selectedItems[0].title}
          onClose={() => setShowShare(false)}
        />
      )}
    </div>
  )
}
