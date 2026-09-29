import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useAlbumPrefsStore } from '../state/albumPrefsStore'
import { fetchAlbumRoot, fetchMyAlbums, createMyAlbum } from '../plex/api'
import { buildThumbUrl } from '../plex/urls'
import type { Album } from '../plex/model'
import { getCachedAlbumChildren, setCachedAlbumChildren } from '../lib/cache'
import { Icon } from '../components/Icon'

function AlbumCard({ album, to, server, accentColor, locked }: { album: Album; to: string; server: NonNullable<ReturnType<typeof useServer>>; accentColor?: string; locked?: boolean }): React.ReactElement {
  return (
    <Link to={to} style={{ textDecoration: 'none', color: 'inherit' }}>
      <div className="grid-cell" style={{ aspectRatio: '1', border: accentColor ? `2px solid ${accentColor}` : undefined }}>
        {album.thumbPath && <img src={buildThumbUrl(server, album.thumbPath, 400, 400)} alt="" loading="lazy" />}
        {locked && <span className="badge badge-favourite">Locked</span>}
        {album.readOnly && <span className="badge badge-favourite">Smart</span>}
      </div>
      <div style={{ marginTop: 6, fontSize: 13 }}>
        <strong>{album.title}</strong>
        <div className="muted">{album.itemCount} items</div>
      </div>
    </Link>
  )
}

export function Albums(): React.ReactElement {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const machineId = useSessionStore((s) => s.machineId)
  const lockedAlbumIds = useLockStore((s) => s.lockedAlbumIds)
  const isAlbumLocked = useLockStore((s) => s.isAlbumLocked)
  const getPrefs = useAlbumPrefsStore((s) => s.getPrefs)
  const navigate = useNavigate()

  const [folders, setFolders] = useState<Album[]>([])
  const [loadingFolders, setLoadingFolders] = useState(true)
  const [folderError, setFolderError] = useState<string | null>(null)

  const [myAlbums, setMyAlbums] = useState<Album[]>([])
  const [loadingMyAlbums, setLoadingMyAlbums] = useState(true)
  const [myAlbumsError, setMyAlbumsError] = useState<string | null>(null)

  const [creating, setCreating] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!server || !sectionKey) return
    let cancelled = false
    void getCachedAlbumChildren(sectionKey, null).then((cached) => {
      if (cached && !cancelled) setFolders(cached.albums)
      if (cached) setLoadingFolders(false)
    })
    fetchAlbumRoot(server, sectionKey)
      .then((data) => {
        if (cancelled) return
        setFolders(data.albums)
        void setCachedAlbumChildren(sectionKey, null, data)
      })
      .catch((e) => !cancelled && setFolderError(e instanceof Error ? e.message : 'Failed to load albums.'))
      .finally(() => !cancelled && setLoadingFolders(false))
    return () => {
      cancelled = true
    }
  }, [server, sectionKey])

  const loadMyAlbums = () => {
    if (!server) return
    setLoadingMyAlbums(true)
    fetchMyAlbums(server)
      .then((data) => setMyAlbums(data))
      .catch((e) => setMyAlbumsError(e instanceof Error ? e.message : 'Failed to load your albums.'))
      .finally(() => setLoadingMyAlbums(false))
  }

  useEffect(() => {
    loadMyAlbums()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [server])

  const handleCreate = async () => {
    if (!server || !machineId || !newTitle.trim() || busy) return
    setBusy(true)
    setCreateError(null)
    try {
      const album = await createMyAlbum(server, machineId, newTitle.trim(), [])
      setCreating(false)
      setNewTitle('')
      loadMyAlbums()
      navigate(`/my-albums/${album.id}`)
    } catch (e) {
      setCreateError(e instanceof Error ? e.message : 'Could not create the album.')
    } finally {
      setBusy(false)
    }
  }

  const visibleFolders = folders.filter((a) => !lockedAlbumIds.includes(a.id))

  if (!server) return <div className="page">Not connected.</div>

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Albums</h1>
      </div>

      <div className="page-header" style={{ marginTop: 0 }}>
        <h2 style={{ fontSize: 16, margin: 0 }}>My albums</h2>
        <button className="btn" onClick={() => setCreating(true)} aria-label="New album">
          <Icon name="plus" size={16} /> New album
        </button>
      </div>

      {creating && (
        <div className="card" style={{ marginBottom: 16 }}>
          {createError && <p style={{ color: 'var(--danger)' }}>{createError}</p>}
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              className="input"
              placeholder="Album title"
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && void handleCreate()}
              autoFocus
              aria-label="New album title"
            />
            <button className="btn btn-primary" onClick={() => void handleCreate()} disabled={busy || !newTitle.trim()}>
              {busy ? 'Creating' : 'Create'}
            </button>
            <button className="btn" onClick={() => { setCreating(false); setCreateError(null); setNewTitle('') }}>
              Cancel
            </button>
          </div>
        </div>
      )}

      {myAlbumsError && <p style={{ color: 'var(--danger)' }}>{myAlbumsError}</p>}
      {loadingMyAlbums && myAlbums.length === 0 && <div className="spinner" />}
      {!loadingMyAlbums && myAlbums.length === 0 && <p className="muted">No albums yet. Create one, or add a photo to an album from the viewer.</p>}
      {myAlbums.length > 0 && (
        <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', marginBottom: 28 }}>
          {myAlbums.map((album) => (
            <AlbumCard key={album.id} album={album} to={`/my-albums/${album.id}`} server={server} />
          ))}
        </div>
      )}

      <div className="page-header" style={{ marginTop: 0 }}>
        <h2 style={{ fontSize: 16, margin: 0 }}>Folders</h2>
      </div>

      {folderError && <p style={{ color: 'var(--danger)' }}>{folderError}</p>}
      {loadingFolders && folders.length === 0 && <div className="spinner" />}
      {!loadingFolders && visibleFolders.length === 0 && <p className="muted">No folder albums yet.</p>}
      <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))' }}>
        {visibleFolders.map((album) => {
          const prefs = getPrefs(album.id)
          return (
            <AlbumCard
              key={album.id}
              album={album}
              to={`/albums/${album.id}`}
              server={server}
              accentColor={prefs.accentColor}
              locked={isAlbumLocked(album.id)}
            />
          )
        })}
      </div>
    </div>
  )
}
