import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useAlbumPrefsStore } from '../state/albumPrefsStore'
import { fetchAlbumRoot } from '../plex/api'
import { buildThumbUrl } from '../plex/urls'
import type { Album } from '../plex/model'
import { getCachedAlbumChildren, setCachedAlbumChildren } from '../lib/cache'

export function Albums(): React.ReactElement {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const lockedAlbumIds = useLockStore((s) => s.lockedAlbumIds)
  const isAlbumLocked = useLockStore((s) => s.isAlbumLocked)
  const getPrefs = useAlbumPrefsStore((s) => s.getPrefs)

  const [albums, setAlbums] = useState<Album[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!server || !sectionKey) return
    let cancelled = false
    void getCachedAlbumChildren(sectionKey, null).then((cached) => {
      if (cached && !cancelled) setAlbums(cached.albums)
      if (cached) setLoading(false)
    })
    fetchAlbumRoot(server, sectionKey)
      .then((data) => {
        if (cancelled) return
        setAlbums(data.albums)
        void setCachedAlbumChildren(sectionKey, null, data)
      })
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Failed to load albums.'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [server, sectionKey])

  const visible = albums.filter((a) => !lockedAlbumIds.includes(a.id))

  if (!server) return <div className="page">Not connected.</div>

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Albums</h1>
      </div>
      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loading && albums.length === 0 && <div className="spinner" />}
      {!loading && visible.length === 0 && <p className="muted">No albums yet.</p>}
      <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))' }}>
        {visible.map((album) => {
          const prefs = getPrefs(album.id)
          return (
            <Link key={album.id} to={`/albums/${album.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
              <div
                className="grid-cell"
                style={{ aspectRatio: '1', border: prefs.accentColor ? `2px solid ${prefs.accentColor}` : undefined }}
              >
                {album.thumbPath && <img src={buildThumbUrl(server, album.thumbPath, 400, 400)} alt="" loading="lazy" />}
                {isAlbumLocked(album.id) && <span className="badge badge-favourite">Locked</span>}
              </div>
              <div style={{ marginTop: 6, fontSize: 13 }}>
                <strong>{album.title}</strong>
                <div className="muted">{album.itemCount} items</div>
              </div>
            </Link>
          )
        })}
      </div>
    </div>
  )
}
