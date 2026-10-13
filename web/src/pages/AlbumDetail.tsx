import { useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useNavigate, useParams } from 'react-router-dom'
import { useServer, useServerScope } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { selectAlbumPrefs, useAlbumPrefsStore, type AlbumSortOrder } from '../state/albumPrefsStore'
import { useSettingsStore } from '../state/settingsStore'
import { errorMessage, showToast } from '../state/toastStore'
import { useSelection } from '../lib/useSelection'
import { useItemList } from '../lib/useItemList'
import { downloadItems, lockItemsOrExplain, reportBulkFailure, runForEach } from '../lib/bulk'
import { appItemLink, viewerPath, type ViewerContext } from '../lib/viewerContext'
import { fetchAlbumChildren, fetchAlbumMeta, editMetadata, deleteItem, rateItem } from '../plex/api'
import { buildDownloadUrl, buildThumbUrl } from '../plex/urls'
import type { Album, MediaItem } from '../plex/model'
import { getCachedAlbumChildren, setCachedAlbumChildren } from '../lib/cache'
import { MediaGrid } from '../components/MediaGrid'
import { SelectionBar } from '../components/SelectionBar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ShareSheet } from '../components/ShareSheet'
import { AddToAlbumDialog } from '../components/AddToAlbumDialog'
import { PasscodeGate } from '../components/PasscodeGate'
import { Icon } from '../components/Icon'

const ACCENTS = ['#e5a00d', '#3dd68c', '#5ab4ff', '#ff6b6b', '#b78cff', '#ff9f43']

export function AlbumDetail(): React.ReactElement {
  const { albumId } = useParams()
  const navigate = useNavigate()
  const server = useServer()
  const scope = useServerScope()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const lockedAlbumIds = useLockStore((s) => s.lockedAlbumIds)
  const unlocked = useLockStore((s) => s.unlocked)
  const lockAlbum = useLockStore((s) => s.lockAlbum)
  const unlockAlbum = useLockStore((s) => s.unlockAlbum)
  // Select this album's prefs (data) so a sort or accent change re-renders the page.
  const prefs = useAlbumPrefsStore(selectAlbumPrefs(albumId))
  const setAccentColor = useAlbumPrefsStore((s) => s.setAccentColor)
  const setSortOrder = useAlbumPrefsStore((s) => s.setSortOrder)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)

  const [albums, setAlbums] = useState<Album[]>([])
  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [editingTitle, setEditingTitle] = useState(false)
  const [description, setDescription] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [showAddToAlbum, setShowAddToAlbum] = useState(false)
  const [busy, setBusy] = useState(false)

  const albumLocked = !!albumId && lockedAlbumIds.includes(albumId)
  // A locked album is only reachable after unlocking, including through a direct link.
  const gated = albumLocked && !unlocked

  useEffect(() => {
    if (!server || !sectionKey || !albumId || !scope || gated) return
    let cancelled = false
    // The cache read and the network request race. Once the network answer has arrived, a
    // slower cache read must not replace it with older data.
    let haveFreshData = false
    void getCachedAlbumChildren(scope, sectionKey, albumId).then((cached) => {
      if (cached && !cancelled && !haveFreshData) {
        setAlbums(cached.albums)
        setItems(cached.items)
        setLoading(false)
      }
    })
    fetchAlbumChildren(server, sectionKey, albumId)
      .then((data) => {
        if (cancelled) return
        haveFreshData = true
        setAlbums(data.albums)
        setItems(data.items)
        setError(null)
        void setCachedAlbumChildren(scope, sectionKey, albumId, data)
      })
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Failed to load album.'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [server, sectionKey, albumId, scope, gated])

  useEffect(() => {
    if (!server || !sectionKey || !albumId || gated) return
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
  }, [server, sectionKey, albumId, gated])

  const sortOrder: AlbumSortOrder = prefs.sortOrder ?? 'newest'

  const sortedItems = useMemo(() => {
    const arr = [...items]
    switch (sortOrder) {
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
  }, [items, sortOrder])

  const list = useItemList(sortedItems, { openAlbumId: albumId })
  const visibleItems = list.visible
  const visibleAlbums = albums.filter((a) => !lockedAlbumIds.includes(a.id))
  const selection = useSelection(visibleItems)
  const basePath = `/albums/${albumId ?? ''}`

  const viewerContext = useMemo<ViewerContext>(
    () => ({ items: visibleItems, basePath, onItemChanged: (id, patch) => list.patch([id], patch), onItemsRemoved: list.remove }),
    [visibleItems, basePath, list],
  )

  if (!server || !sectionKey || !albumId) return <div className="page">Not connected.</div>
  if (gated) return <PasscodeGate message="This album is locked. Enter your passcode to open it." />

  const openViewer = (item: MediaItem) => navigate(viewerPath(basePath, item.id))

  const handleRename = async () => {
    setEditingTitle(false)
    if (!title.trim()) return
    try {
      await editMetadata(server, { sectionKey, ratingKey: albumId, type: '14', title: title.trim() })
    } catch (e) {
      showToast(errorMessage(e, 'Could not rename the album.'))
    }
  }

  const handleDescription = async () => {
    try {
      await editMetadata(server, { sectionKey, ratingKey: albumId, type: '14', summary: description })
    } catch (e) {
      showToast(errorMessage(e, 'Could not save the description.'))
    }
  }

  const handleLockToggle = () => {
    if (albumLocked) {
      if (!unlockAlbum(albumId)) showToast('Unlock on the Locked page first.', 'info')
      return
    }
    if (!lockAlbum(albumId)) {
      showToast('Set a passcode on the Locked page before locking albums.', 'info')
      return
    }
    if (!unlocked) navigate('/albums')
  }

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

  const handleDeleteSelected = async () => {
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
        <div>
          <Link to="/albums" className="muted" style={{ fontSize: 13, textDecoration: 'none' }}>
            ← Albums
          </Link>
          {editingTitle ? (
            <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} onBlur={() => void handleRename()} onKeyDown={(e) => e.key === 'Enter' && void handleRename()} autoFocus aria-label="Album title" />
          ) : (
            <h1 className="page-title" onDoubleClick={() => setEditingTitle(true)} style={{ cursor: 'text' }} title="Double click to rename">
              {title || 'Album'}
            </h1>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <select className="input" style={{ width: 'auto' }} value={sortOrder} onChange={(e) => setSortOrder(albumId, e.target.value as AlbumSortOrder)} aria-label="Sort order">
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
                aria-pressed={prefs.accentColor === c}
                style={{ width: 20, height: 20, borderRadius: '50%', background: c, border: prefs.accentColor === c ? '2px solid #fff' : 'none', cursor: 'pointer' }}
              />
            ))}
          </div>
          <button className="btn" onClick={handleLockToggle}>
            <Icon name={albumLocked ? 'unlock' : 'lock'} size={16} /> {albumLocked ? 'Unlock' : 'Lock'}
          </button>
        </div>
      </div>

      <textarea className="input" placeholder="Description" aria-label="Description" value={description} onChange={(e) => setDescription(e.target.value)} onBlur={() => void handleDescription()} rows={2} style={{ marginBottom: 16, resize: 'vertical' }} />

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loading && <div className="spinner" />}

      {visibleAlbums.length > 0 && (
        <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', marginBottom: 20 }}>
          {visibleAlbums.map((a) => (
            <Link key={a.id} to={`/albums/${a.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
              <div className="grid-cell" style={{ aspectRatio: '1' }}>
                {a.thumbPath && <img src={buildThumbUrl(server, a.thumbPath, 320, 320)} alt="" loading="lazy" />}
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
          groupByMonth={sortOrder === 'newest' || sortOrder === 'oldest'}
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
          link={appItemLink(basePath, selectedItems[0].id)}
          fileUrl={buildDownloadUrl(server, selectedItems[0].partKey)}
          fileName={selectedItems[0].title}
          onClose={() => setShowShare(false)}
        />
      )}

      <Outlet context={viewerContext} />
    </div>
  )
}
