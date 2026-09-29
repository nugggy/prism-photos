import { useEffect, useState } from 'react'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { addToMyAlbum, createMyAlbum, fetchMyAlbums } from '../plex/api'
import type { Album } from '../plex/model'
import { Icon } from './Icon'

export interface AddToAlbumDialogProps {
  /** Rating keys of the item(s) to add. */
  itemIds: string[]
  onClose: () => void
  /** Called once the items have been added or a new album created, so lists can be refreshed. */
  onAdded?: (album: Album) => void
}

/** Reusable "Add to album" picker: lists editable My albums, with New album at the top. */
export function AddToAlbumDialog({ itemIds, onClose, onAdded }: AddToAlbumDialogProps): React.ReactElement {
  const server = useServer()
  const machineId = useSessionStore((s) => s.machineId)

  const [albums, setAlbums] = useState<Album[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [creating, setCreating] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [confirmation, setConfirmation] = useState<string | null>(null)

  useEffect(() => {
    if (!server) return
    let cancelled = false
    fetchMyAlbums(server)
      .then((data) => {
        if (!cancelled) setAlbums(data.filter((a) => !a.readOnly))
      })
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Failed to load albums.'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [server])

  const finish = (album: Album) => {
    setConfirmation(`Added to ${album.title}.`)
    onAdded?.(album)
    setTimeout(onClose, 1100)
  }

  const handleSelect = async (album: Album) => {
    if (!server || busy) return
    setBusy(true)
    setError(null)
    try {
      await addToMyAlbum(server, machineId ?? '', album.id, itemIds)
      finish(album)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add to that album.')
    } finally {
      setBusy(false)
    }
  }

  const handleCreate = async () => {
    if (!server || !machineId || !newTitle.trim() || busy) return
    setBusy(true)
    setError(null)
    try {
      const album = await createMyAlbum(server, machineId, newTitle.trim(), itemIds)
      finish(album)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the album.')
    } finally {
      setBusy(false)
    }
  }

  const itemLabel = itemIds.length === 1 ? '1 item' : `${itemIds.length} items`

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Add to album"
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200 }}
      onClick={onClose}
    >
      <div className="card" style={{ width: 360, maxWidth: '90%', maxHeight: '80vh', overflowY: 'auto' }} onClick={(e) => e.stopPropagation()}>
        <h2 style={{ marginTop: 0, fontSize: 18 }}>Add to album</h2>
        {!confirmation && <p className="muted" style={{ marginTop: -8 }}>{itemLabel}</p>}

        {confirmation ? (
          <p style={{ color: 'var(--gold)' }}>{confirmation}</p>
        ) : (
          <>
            {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}

            {creating ? (
              <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                <input
                  className="input"
                  placeholder="Album title"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && void handleCreate()}
                  autoFocus
                  aria-label="New album title"
                />
                <button className="btn btn-primary" onClick={() => void handleCreate()} disabled={busy || !newTitle.trim()} aria-label="Create album">
                  Create
                </button>
              </div>
            ) : (
              <button
                className="btn"
                style={{ width: '100%', marginBottom: 12, justifyContent: 'flex-start', gap: 8 }}
                onClick={() => setCreating(true)}
                aria-label="New album"
              >
                <Icon name="plus" size={16} /> New album
              </button>
            )}

            {loading && <div className="spinner" />}
            {!loading && albums.length === 0 && <p className="muted">No albums yet. Create one above.</p>}

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {albums.map((a) => (
                <button
                  key={a.id}
                  className="btn"
                  style={{ justifyContent: 'space-between' }}
                  onClick={() => void handleSelect(a)}
                  disabled={busy}
                  aria-label={`Add to ${a.title}`}
                >
                  <span>{a.title}</span>
                  <span className="muted">{a.itemCount}</span>
                </button>
              ))}
            </div>
          </>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
          <button className="btn" onClick={onClose} aria-label="Close">
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
