import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useAlbumPrefsStore, type AlbumSortOrder } from '../state/albumPrefsStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useViewerListStore } from '../state/viewerListStore'
import { fetchAlbumChildren, fetchAlbumMeta, editMetadata, deleteItem, rateItem } from '../plex/api'
import { buildDownloadUrl, buildThumbUrl } from '../plex/urls'
import type { Album, MediaItem } from '../plex/model'
import { filterLocked } from '../plex/timeline'
import { getCachedAlbumChildren, setCachedAlbumChildren } from '../lib/cache'
import { MediaGrid } from '../components/MediaGrid'
import { SelectionBar } from '../components/SelectionBar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ShareSheet } from '../components/ShareSheet'
import { AddToAlbumDialog } from '../components/AddToAlbumDialog'
import { downloadUrl } from '../lib/download'
import { Icon } from '../components/Icon'

const ACCENTS = ['#e5a00d', '#3dd68c', '#5ab4ff', '#ff6b6b', '#b78cff', '#ff9f43']

export function AlbumDetail(): React.ReactElement {
  const { albumId } = useParams()
  const navigate = useNavigate()
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const lockedAlbumIds = useLockStore((s) => s.lockedAlbumIds)
  const lockItem = useLockStore((s) => s.lockItem)
  const lockAlbum = useLockStore((s) => s.lockAlbum)
  const unlockAlbum = useLockStore((s) => s.unlockAlbum)
  const isAlbumLocked = useLockStore((s) => s.isAlbumLocked)
  const getPrefs = useAlbumPrefsStore((s) => s.getPrefs)
  const setAccentColor = useAlbumPrefsStore((s) => s.setAccentColor)
  const setSortOrder = useAlbumPrefsStore((s) => s.setSortOrder)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const setViewerList = useViewerListStore((s) => s.setList)

  const [albums, setAlbums] = useState<Album[]>([])
  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [editingTitle, setEditingTitle] = useState(false)
  const [description, setDescription] = useState('')
  const [itemOverrides, setItemOverrides] = useState<Record<string, Partial<MediaItem>>>({})
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [showAddToAlbum, setShowAddToAlbum] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!server || !sectionKey || !albumId) return
    let cancelled = false
    void getCachedAlbumChildren(sectionKey, albumId).then((cached) => {
      if (cached && !cancelled) {
        setAlbums(cached.albums)
        setItems(cached.items)
        setLoading(false)
      }
    })
    fetchAlbumChildren(server, sectionKey, albumId)
      .then((data) => {
        if (cancelled) return
        setAlbums(data.albums)
        setItems(data.items)
        void setCachedAlbumChildren(sectionKey, albumId, data)
      })
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Failed to load album.'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [server, sectionKey, albumId])

  useEffect(() => {
    if (!server || !sectionKey || !albumId) return
    let cancelled = false
    fetchAlbumMeta(server, sectionKey, albumId)
      .then((meta) => {
        if (cancelled || !meta) return
        setTitle(meta.title)
        setDescription(meta.summary)
      })
      .catch(() => {
        /* title/description prefill is best effort */
      })
    return () => {
      cancelled = true
    }
  }, [server, sectionKey, albumId])

  const prefs = albumId ? getPrefs(albumId) : {}

  const sortedItems = useMemo(() => {
    const order: AlbumSortOrder = prefs.sortOrder ?? 'newest'
    const arr = items.map((it) => (itemOverrides[it.id] ? { ...it, ...itemOverrides[it.id] } : it))
    switch (order) {
      case 'oldest':
        return arr.sort((a, b) => a.takenAt - b.takenAt)
      case 'titleAsc':
        return arr.sort((a, b) => a.title.localeCompare(b.title))
      case 'titleDesc':
        return arr.sort((a, b) => b.title.localeCompare(a.title))
      case 'newest':
      default:
        return arr.sort((a, b) => b.takenAt - a.takenAt)
    }
  }, [items, itemOverrides, prefs.sortOrder])

  const visibleItems = filterLocked(sortedItems, new Set(lockedItemIds))
  const visibleAlbums = albums.filter((a) => !lockedAlbumIds.includes(a.id))
  const selection = useSelection(visibleItems)

  if (!server || !sectionKey || !albumId) return <div className="page">Not connected.</div>

  const openViewer = (_item: MediaItem, index: number) => {
    setViewerList(`album-${albumId}`, visibleItems)
    navigate(`/view/album-${albumId}/${index}`)
  }

  const handleRename = async () => {
    if (!title.trim()) {
      setEditingTitle(false)
      return
    }
    await editMetadata(server, { sectionKey, ratingKey: albumId, type: '14', title: title.trim() })
    setEditingTitle(false)
  }

  const handleDescription = async () => {
    await editMetadata(server, { sectionKey, ratingKey: albumId, type: '14', summary: description })
  }

  const selectedItems = visibleItems.filter((i) => selection.selectedIds.has(i.id))

  const handleFavourite = async () => {
    setBusy(true)
    const makeFav = !selectedItems.every((i) => i.favourite)
    try {
      await Promise.all(selectedItems.map((i) => rateItem(server, i.id, makeFav)))
      setItemOverrides((prev) => {
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

  const handleDeleteSelected = async () => {
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
        <div>
          <Link to="/albums" className="muted" style={{ fontSize: 13, textDecoration: 'none' }}>
            ← Albums
          </Link>
          {editingTitle ? (
            <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} onBlur={() => void handleRename()} onKeyDown={(e) => e.key === 'Enter' && void handleRename()} autoFocus />
          ) : (
            <h1 className="page-title" onDoubleClick={() => setEditingTitle(true)} style={{ cursor: 'text' }} title="Double click to rename">
              {title || 'Album'}
            </h1>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <select className="input" style={{ width: 'auto' }} value={prefs.sortOrder ?? 'newest'} onChange={(e) => setSortOrder(albumId, e.target.value as AlbumSortOrder)}>
            <option value="newest">Newest first</option>
            <option value="oldest">Oldest first</option>
            <option value="titleAsc">Title A-Z</option>
            <option value="titleDesc">Title Z-A</option>
          </select>
          <div style={{ display: 'flex', gap: 4 }}>
            {ACCENTS.map((c) => (
              <button
                key={c}
                onClick={() => setAccentColor(albumId, c)}
                aria-label={`Accent colour ${c}`}
                style={{ width: 20, height: 20, borderRadius: '50%', background: c, border: prefs.accentColor === c ? '2px solid #fff' : 'none', cursor: 'pointer' }}
              />
            ))}
          </div>
          <button className="btn" onClick={() => (isAlbumLocked(albumId) ? unlockAlbum(albumId) : lockAlbum(albumId))}>
            <Icon name={isAlbumLocked(albumId) ? 'unlock' : 'lock'} size={16} /> {isAlbumLocked(albumId) ? 'Unlock' : 'Lock'}
          </button>
        </div>
      </div>

      <textarea className="input" placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} onBlur={() => void handleDescription()} rows={2} style={{ marginBottom: 16, resize: 'vertical' }} />

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loading && <div className="spinner" />}

      {visibleAlbums.length > 0 && (
        <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', marginBottom: 20 }}>
          {visibleAlbums.map((a) => (
            <Link key={a.id} to={`/albums/${a.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
              <div className="grid-cell" style={{ aspectRatio: '1' }}>
                {a.thumbPath && <img src={buildThumbUrl(server, a.thumbPath, 300, 300)} alt="" loading="lazy" />}
              </div>
              <div style={{ marginTop: 6, fontSize: 13 }}>{a.title}</div>
            </Link>
          ))}
        </div>
      )}

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
      {!loading && visibleAlbums.length === 0 && visibleItems.length === 0 && <p className="muted">This album is empty.</p>}

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
          onConfirm={() => void handleDeleteSelected()}
          onCancel={() => setConfirmDelete(false)}
        />
      )}

      {showShare && selectedItems.length === 1 && (
        <ShareSheet
          title={selectedItems[0].title}
          link={`${window.location.origin}${window.location.pathname}#/view/album-${albumId}/0`}
          fileUrl={buildDownloadUrl(server, selectedItems[0].partKey)}
          fileName={selectedItems[0].title}
          onClose={() => setShowShare(false)}
        />
      )}
    </div>
  )
}
