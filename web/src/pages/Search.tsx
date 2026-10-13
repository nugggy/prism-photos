import { useEffect, useMemo, useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useItemList } from '../lib/useItemList'
import { downloadItems, lockItemsOrExplain, reportBulkFailure, runForEach } from '../lib/bulk'
import { appItemLink, viewerPath, type ViewerContext } from '../lib/viewerContext'
import { searchLibrary, deleteItem, rateItem } from '../plex/api'
import { buildDownloadUrl } from '../plex/urls'
import type { MediaItem } from '../plex/model'
import { MediaGrid } from '../components/MediaGrid'
import { SelectionBar } from '../components/SelectionBar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ShareSheet } from '../components/ShareSheet'
import { AddToAlbumDialog } from '../components/AddToAlbumDialog'

const BASE_PATH = '/search'

const CURRENT_YEAR = new Date().getFullYear()
const YEARS = Array.from({ length: 8 }, (_, i) => CURRENT_YEAR - i)

export function Search(): React.ReactElement {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const navigate = useNavigate()

  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [tag, setTag] = useState<string | null>(null)
  const [year, setYear] = useState<number | null>(null)
  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [showAddToAlbum, setShowAddToAlbum] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 350)
    return () => clearTimeout(t)
  }, [query])

  useEffect(() => {
    if (!server || !sectionKey) return
    if (!debounced && !tag && !year) {
      setItems([])
      return
    }
    let cancelled = false
    setLoading(true)
    searchLibrary(server, sectionKey, { query: debounced || undefined, tag: tag ?? undefined, year: year ?? undefined })
      .then((data) => {
        if (cancelled) return
        setItems(data)
        setError(null)
      })
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Search failed.'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [server, sectionKey, debounced, tag, year])

  const tagOptions = useMemo(() => {
    const set = new Set<string>()
    items.forEach((i) => i.tags.forEach((t) => set.add(t)))
    return Array.from(set).slice(0, 12)
  }, [items])

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
    const makeFav = !selectedItems.every((i) => i.favourite)
    try {
      const result = await runForEach(
        selectedItems.map((i) => i.id),
        (id) => rateItem(server, id, makeFav),
      )
      list.patch(result.succeeded, { favourite: makeFav })
      reportBulkFailure(result, makeFav ? 'favourite' : 'unfavourite')
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
    } finally {
      setBusy(false)
      setConfirmDelete(false)
      selection.clear()
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Search</h1>
      </div>
      <input className="input" placeholder="Search by title, tag, place or camera model" value={query} onChange={(e) => setQuery(e.target.value)} style={{ marginBottom: 12 }} />

      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 6 }}>
        {YEARS.map((y) => (
          <button key={y} className={`chip${year === y ? ' active' : ''}`} onClick={() => setYear(year === y ? null : y)}>
            {y}
          </button>
        ))}
      </div>
      {tagOptions.length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 16 }}>
          {tagOptions.map((t) => (
            <button key={t} className={`chip${tag === t ? ' active' : ''}`} onClick={() => setTag(tag === t ? null : t)}>
              {t}
            </button>
          ))}
        </div>
      )}

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loading && <div className="spinner" />}
      {!loading && !debounced && !tag && !year && <p className="muted">Start typing to search your library.</p>}
      {!loading && (debounced || tag || year) && visibleItems.length === 0 && <p className="muted">No results.</p>}

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
          link={appItemLink('', selectedItems[0].id)}
          fileUrl={buildDownloadUrl(server, selectedItems[0].partKey)}
          fileName={selectedItems[0].title}
          onClose={() => setShowShare(false)}
        />
      )}

      <Outlet context={viewerContext} />
    </div>
  )
}
