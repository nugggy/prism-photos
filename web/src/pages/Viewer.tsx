import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useViewerListStore } from '../state/viewerListStore'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useSettingsStore } from '../state/settingsStore'
import { useAlbumPrefsStore } from '../state/albumPrefsStore'
import { buildDownloadUrl, buildPlexWebLink, buildThumbUrl } from '../plex/urls'
import { deleteItem, editMetadata, rateItem, setAlbumCover } from '../plex/api'
import { ZoomableImage } from '../components/ZoomableImage'
import { VideoPlayer } from '../components/VideoPlayer'
import { InfoPanel } from '../components/InfoPanel'
import { ShareSheet } from '../components/ShareSheet'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Icon } from '../components/Icon'
import { downloadUrl } from '../lib/download'

export function Viewer(): React.ReactElement {
  const { index: indexParam } = useParams()
  const navigate = useNavigate()
  const items = useViewerListStore((s) => s.items)
  const source = useViewerListStore((s) => s.source)
  const server = useServer()
  const machineId = useSessionStore((s) => s.machineId)
  const lockItem = useLockStore((s) => s.lockItem)
  const unlockItem = useLockStore((s) => s.unlockItem)
  const isItemLocked = useLockStore((s) => s.isItemLocked)
  const transcodePreference = useSettingsStore((s) => s.transcodePreference)
  const slideshowIntervalSec = useSettingsStore((s) => s.slideshowIntervalSec)
  const setCover = useAlbumPrefsStore((s) => s.setCover)

  const [index, setIndex] = useState(() => Number(indexParam ?? 0))
  const [showInfo, setShowInfo] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [slideshowOn, setSlideshowOn] = useState(false)
  const [renaming, setRenaming] = useState(false)
  const [titleDraft, setTitleDraft] = useState('')
  const [localFavourites, setLocalFavourites] = useState<Record<string, boolean>>({})

  const containerRef = useRef<HTMLDivElement>(null)
  const item = items[index]

  useEffect(() => {
    setIndex(Number(indexParam ?? 0))
  }, [indexParam])

  const goTo = useCallback(
    (next: number) => {
      if (next < 0 || next >= items.length) return
      setIndex(next)
      navigate(`/view/${source}/${next}`, { replace: true })
    },
    [items.length, navigate, source],
  )
  const next = useCallback(() => goTo(index + 1), [goTo, index])
  const prev = useCallback(() => goTo(index - 1), [goTo, index])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') next()
      else if (e.key === 'ArrowLeft') prev()
      else if (e.key === 'Escape') navigate(-1)
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [next, prev, navigate])

  useEffect(() => {
    if (!slideshowOn) return
    const id = setInterval(() => {
      setIndex((i) => {
        const n = i + 1 >= items.length ? 0 : i + 1
        navigate(`/view/${source}/${n}`, { replace: true })
        return n
      })
    }, slideshowIntervalSec * 1000)
    return () => clearInterval(id)
  }, [slideshowOn, slideshowIntervalSec, items.length, navigate, source])

  // Preload neighbours.
  useEffect(() => {
    if (!server) return
    ;[index - 1, index + 1].forEach((i) => {
      const neighbour = items[i]
      if (neighbour && neighbour.kind === 'photo') {
        const img = new Image()
        img.src = buildThumbUrl(server, neighbour.thumbPath, 1600, 1600)
      }
    })
  }, [index, items, server])

  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen()
    else void containerRef.current?.requestFullscreen()
  }

  const favourite = item ? (localFavourites[item.id] ?? item.favourite) : false

  const handleFavourite = async () => {
    if (!server || !item) return
    const next = !favourite
    setLocalFavourites((p) => ({ ...p, [item.id]: next }))
    await rateItem(server, item.id, next)
  }

  const handleDelete = async () => {
    if (!server || !item) return
    await deleteItem(server, item.id)
    setConfirmDelete(false)
    navigate(-1)
  }

  const handleLockToggle = () => {
    if (!item) return
    if (isItemLocked(item.id)) unlockItem(item.id)
    else lockItem(item.id)
  }

  const handleRename = async () => {
    if (!server || !item) return
    await editMetadata(server, { sectionKey: item.sectionKey, ratingKey: item.id, type: item.kind === 'video' ? '12' : '13', title: titleDraft })
    setRenaming(false)
  }

  const handleSetAsCover = async () => {
    if (!server || !item?.albumId) return
    setCover(item.albumId, item.id)
    try {
      await setAlbumCover(server, item.albumId, buildThumbUrl(server, item.thumbPath, 800, 800))
    } catch {
      /* local override still applies even if the server call is unsupported */
    }
  }

  const plexLink = useMemo(() => (machineId && item ? buildPlexWebLink(machineId, item.id) : ''), [machineId, item])

  if (!item || !server) {
    return (
      <div className="center-screen">
        <p>Nothing to show.</p>
      </div>
    )
  }

  return (
    <div ref={containerRef} style={{ position: 'fixed', inset: 0, background: '#000', color: '#fff', zIndex: 50, display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'rgba(0,0,0,0.6)' }}>
        <button className="btn" onClick={() => navigate(-1)} aria-label="Close viewer">
          <Icon name="close" size={18} />
        </button>
        {renaming ? (
          <input className="input" style={{ maxWidth: 300 }} value={titleDraft} onChange={(e) => setTitleDraft(e.target.value)} onBlur={() => void handleRename()} onKeyDown={(e) => e.key === 'Enter' && void handleRename()} autoFocus />
        ) : (
          <strong onDoubleClick={() => { setTitleDraft(item.title); setRenaming(true) }} style={{ cursor: 'text' }}>
            {item.title}
          </strong>
        )}
        <div style={{ display: 'flex', gap: 6 }}>
          <button className="btn" onClick={() => void handleFavourite()} aria-label="Favourite">
            <Icon name={favourite ? 'heartFilled' : 'heart'} size={18} />
          </button>
          <button className="btn" onClick={handleLockToggle} aria-label="Lock">
            <Icon name={isItemLocked(item.id) ? 'unlock' : 'lock'} size={18} />
          </button>
          <button className="btn" onClick={() => setShowShare(true)} aria-label="Share">
            <Icon name="share" size={18} />
          </button>
          <button className="btn" onClick={() => downloadUrl(buildDownloadUrl(server, item.partKey), item.title)} aria-label="Download">
            <Icon name="download" size={18} />
          </button>
          {item.albumId && (
            <button className="btn" onClick={() => void handleSetAsCover()} aria-label="Set as album cover">
              Cover
            </button>
          )}
          <button className="btn" onClick={() => setShowInfo((v) => !v)} aria-label="Info">
            <Icon name="info" size={18} />
          </button>
          <button className={`btn${slideshowOn ? ' btn-primary' : ''}`} onClick={() => setSlideshowOn((v) => !v)} aria-label="Slideshow">
            Slideshow
          </button>
          {plexLink && (
            <a className="btn" href={plexLink} target="_blank" rel="noopener noreferrer">
              Open in Plex
            </a>
          )}
          <button className="btn" onClick={toggleFullscreen} aria-label="Fullscreen">
            <Icon name="fullscreen" size={18} />
          </button>
          <button className="btn btn-danger" onClick={() => setConfirmDelete(true)} aria-label="Delete">
            <Icon name="trash" size={18} />
          </button>
        </div>
      </div>

      <div style={{ position: 'relative', flex: 1, minHeight: 0 }}>
        {item.kind === 'photo' ? (
          <ZoomableImage src={buildThumbUrl(server, item.thumbPath, 2000, 2000)} alt={item.title} onSwipeLeft={next} onSwipeRight={prev} />
        ) : (
          <VideoPlayer item={item} server={server} transcodePreference={transcodePreference} />
        )}

        <button className="btn" onClick={prev} aria-label="Previous" style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }} disabled={index === 0}>
          <Icon name="arrowLeft" size={18} />
        </button>
        <button className="btn" onClick={next} aria-label="Next" style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)' }} disabled={index === items.length - 1}>
          <Icon name="arrowRight" size={18} />
        </button>

        {showInfo && (
          <InfoPanel
            item={item}
            onClose={() => setShowInfo(false)}
            onSaveSummary={(summary) => void editMetadata(server, { sectionKey: item.sectionKey, ratingKey: item.id, type: item.kind === 'video' ? '12' : '13', summary })}
            onSaveTags={(tags) => {
              const added = tags.filter((t) => !item.tags.includes(t))
              const removed = item.tags.filter((t) => !tags.includes(t))
              void editMetadata(server, { sectionKey: item.sectionKey, ratingKey: item.id, type: item.kind === 'video' ? '12' : '13', addTags: added, removeTags: removed })
            }}
          />
        )}
      </div>

      {showShare && (
        <ShareSheet
          title={item.title}
          link={plexLink || window.location.href}
          fileUrl={buildDownloadUrl(server, item.partKey)}
          fileName={item.title}
          onClose={() => setShowShare(false)}
        />
      )}
      {confirmDelete && (
        <ConfirmDialog title="Delete item" message="This deletes the item from Plex permanently. This cannot be undone." confirmLabel="Delete" danger onConfirm={() => void handleDelete()} onCancel={() => setConfirmDelete(false)} />
      )}
    </div>
  )
}
