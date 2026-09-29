import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useViewerListStore } from '../state/viewerListStore'
import { deleteMyAlbum, fetchMyAlbumItems, fetchMyAlbums, removeFromMyAlbum, renameMyAlbum } from '../plex/api'
import { buildDownloadUrl } from '../plex/urls'
import type { Album, MediaItem } from '../plex/model'
import { filterLocked } from '../plex/timeline'
import { MediaGrid } from '../components/MediaGrid'
import { SelectionBar } from '../components/SelectionBar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ShareSheet } from '../components/ShareSheet'
import { Icon } from '../components/Icon'
import { downloadUrl } from '../lib/download'

export function MyAlbumDetail(): React.ReactElement {
  const { id } = useParams()
  const navigate = useNavigate()
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const setViewerList = useViewerListStore((s) => s.setList)

  const [album, setAlbum] = useState<Album | null>(null)
  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [menuOpen, setMenuOpen] = useState(false)
  const [renaming, setRenaming] = useState(false)
  const [titleDraft, setTitleDraft] = useState('')
  const [confirmDeleteAlbum, setConfirmDeleteAlbum] = useState(false)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [busy, setBusy] = useState(false)

  const load = () => {
    if (!server || !sectionKey || !id) return
    setLoading(true)
    Promise.all([fetchMyAlbums(server), fetchMyAlbumItems(server, sectionKey, id)])
      .then(([albums, playlistItems]) => {
        setAlbum(albums.find((a) => a.id === id) ?? null)
        setItems(playlistItems)
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load album.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [server, sectionKey, id])

  const visibleItems = filterLocked(items, new Set(lockedItemIds))
  const selection = useSelection(visibleItems)

  if (!server || !sectionKey || !id) return <div className="page">Not connected.</div>

  const readOnly = album?.readOnly === true

  const openViewer = (_item: MediaItem, index: number) => {
    setViewerList(`myalbum:${id}`, visibleItems)
    navigate(`/view/myalbum:${id}/${index}`)
  }

  const selectedItems = visibleItems.filter((i) => selection.selectedIds.has(i.id))

  const handleDownload = () => {
    selectedItems.forEach((i) => downloadUrl(buildDownloadUrl(server, i.partKey), i.title))
    selection.clear()
  }

  const handleRemoveSelected = async () => {
    setBusy(true)
    try {
      await removeFromMyAlbum(server, id, selectedItems.map((i) => i.id))
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not remove the selected item(s).')
    } finally {
      setBusy(false)
      setConfirmRemove(false)
      selection.clear()
    }
  }

  const handleRename = async () => {
    if (!titleDraft.trim()) {
      setRenaming(false)
      return
    }
    setBusy(true)
    try {
      await renameMyAlbum(server, id, titleDraft.trim())
      setRenaming(false)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not rename the album.')
    } finally {
      setBusy(false)
    }
  }

  const handleDeleteAlbum = async () => {
    setBusy(true)
    try {
      await deleteMyAlbum(server, id)
      navigate('/albums')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete the album.')
      setBusy(false)
      setConfirmDeleteAlbum(false)
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <Link to="/albums" className="muted" style={{ fontSize: 13, textDecoration: 'none' }}>
            ← Albums
          </Link>
          {renaming ? (
            <input
              className="input"
              value={titleDraft}
              onChange={(e) => setTitleDraft(e.target.value)}
              onBlur={() => void handleRename()}
              onKeyDown={(e) => e.key === 'Enter' && void handleRename()}
              autoFocus
              aria-label="Album title"
            />
          ) : (
            <h1 className="page-title">
              {album?.title ?? 'Album'}
              {readOnly && <span className="muted" style={{ fontSize: 13, fontWeight: 400, marginLeft: 8 }}>Smart (read-only)</span>}
            </h1>
          )}
        </div>

        {!readOnly && (
          <div style={{ position: 'relative' }}>
            <button className="btn" onClick={() => setMenuOpen((v) => !v)} aria-label="Album menu">
              <Icon name="more" size={18} />
            </button>
            {menuOpen && (
              <div
                className="card"
                style={{ position: 'absolute', right: 0, top: '110%', zIndex: 30, padding: 6, display: 'flex', flexDirection: 'column', gap: 2, minWidth: 160 }}
              >
                <button
                  className="btn"
                  style={{ justifyContent: 'flex-start' }}
                  onClick={() => {
                    setTitleDraft(album?.title ?? '')
                    setRenaming(true)
                    setMenuOpen(false)
                  }}
                >
                  <Icon name="edit" size={16} /> Rename
                </button>
                <button
                  className="btn btn-danger"
                  style={{ justifyContent: 'flex-start' }}
                  onClick={() => {
                    setConfirmDeleteAlbum(true)
                    setMenuOpen(false)
                  }}
                >
                  <Icon name="trash" size={16} /> Delete album
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loading && <div className="spinner" />}
      {!loading && visibleItems.length === 0 && <p className="muted">This album is empty.</p>}

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
        onRemoveFromAlbum={readOnly ? undefined : () => setConfirmRemove(true)}
        onClear={selection.clear}
      />

      {confirmRemove && (
        <ConfirmDialog
          title="Remove from album"
          message={`Remove ${selectedItems.length} item(s) from this album? The item(s) stay in your library.`}
          confirmLabel={busy ? 'Removing' : 'Remove'}
          danger
          onConfirm={() => void handleRemoveSelected()}
          onCancel={() => setConfirmRemove(false)}
        />
      )}

      {confirmDeleteAlbum && (
        <ConfirmDialog
          title="Delete album"
          message="This deletes the album everywhere it shows in Plex. The photos and videos in it are not deleted. This cannot be undone."
          confirmLabel={busy ? 'Deleting' : 'Delete'}
          danger
          onConfirm={() => void handleDeleteAlbum()}
          onCancel={() => setConfirmDeleteAlbum(false)}
        />
      )}

      {showShare && selectedItems.length === 1 && (
        <ShareSheet
          title={selectedItems[0].title}
          link={`${window.location.origin}${window.location.pathname}#/view/myalbum:${id}/0`}
          fileUrl={buildDownloadUrl(server, selectedItems[0].partKey)}
          fileName={selectedItems[0].title}
          onClose={() => setShowShare(false)}
        />
      )}
    </div>
  )
}
