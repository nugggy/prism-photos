import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useViewerListStore } from '../state/viewerListStore'
import { searchLibrary, deleteItem, rateItem } from '../plex/api'
import { buildDownloadUrl } from '../plex/urls'
import type { MediaItem } from '../plex/model'
import { filterLocked } from '../plex/timeline'
import { MediaGrid } from '../components/MediaGrid'
import { SelectionBar } from '../components/SelectionBar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ShareSheet } from '../components/ShareSheet'
import { AddToAlbumDialog } from '../components/AddToAlbumDialog'
import { downloadUrl } from '../lib/download'

const CURRENT_YEAR = new Date().getFullYear()
const YEARS = Array.from({ length: 8 }, (_, i) => CURRENT_YEAR - i)

export function Search(): React.ReactElement {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const lockItem = useLockStore((s) => s.lockItem)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const setViewerList = useViewerListStore((s) => s.setList)
  const navigate = useNavigate()

  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [tag, setTag] = useState<string | null>(null)
  const [year, setYear] = useState<number | null>(null)
  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [overrides, setOverrides] = useState<Record<string, Partial<MediaItem>>>({})
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
      .then((data) => !cancelled && setItems(data))
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

  const visibleItems = useMemo(() => {
    const withOverrides = items.map((it) => (overrides[it.id] ? { ...it, ...overrides[it.id] } : it))
    return filterLocked(withOverrides, new Set(lockedItemIds))
  }, [items, overrides, lockedItemIds])
  const selection = useSelection(visibleItems)

  if (!server) return <div className="page">Not connected.</div>

  const openViewer = (_item: MediaItem, index: number) => {
    setViewerList('search', visibleItems)
    navigate(`/view/search/${index}`)
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
      setItems((prev) => prev.filter((i) => !selection.selectedIds.has(i.id)))
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
          link={`${window.location.origin}${window.location.pathname}#/view/search/0`}
          fileUrl={buildDownloadUrl(server, selectedItems[0].partKey)}
          fileName={selectedItems[0].title}
          onClose={() => setShowShare(false)}
        />
      )}
    </div>
  )
}
