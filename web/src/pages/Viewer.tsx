import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useSettingsStore } from '../state/settingsStore'
import { useAlbumPrefsStore } from '../state/albumPrefsStore'
import { errorMessage, showToast } from '../state/toastStore'
import { VIEWER_IMAGE_SIZE, buildDownloadUrl, buildPlexWebLink, buildThumbUrl } from '../plex/urls'
import { deleteItem, editMetadata, fetchMetadata, rateItem, setAlbumCover } from '../plex/api'
import type { MediaItem } from '../plex/model'
import { ZoomableImage } from '../components/ZoomableImage'
import { VideoPlayer } from '../components/VideoPlayer'
import { InfoPanel } from '../components/InfoPanel'
import { ShareSheet } from '../components/ShareSheet'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { AddToAlbumDialog } from '../components/AddToAlbumDialog'
import { PasscodeGate } from '../components/PasscodeGate'
import { Icon } from '../components/Icon'
import { downloadUrl } from '../lib/download'
import { lockItemsOrExplain } from '../lib/bulk'
import { isEditableTarget } from '../lib/keyboard'
import { useViewerContext, viewerPath } from '../lib/viewerContext'

interface SingleItem {
  id: string
  item: MediaItem | null
  error: string | null
}

const OVERLAY_STYLE: React.CSSProperties = { position: 'fixed', inset: 0, background: '#000', color: '#fff', zIndex: 50, display: 'flex', flexDirection: 'column', outline: 'none' }

export function Viewer(): React.ReactElement {
  const { itemId = '' } = useParams()
  const ctx = useViewerContext()
  const navigate = useNavigate()
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const machineId = useSessionStore((s) => s.machineId)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const lockedAlbumIds = useLockStore((s) => s.lockedAlbumIds)
  const unlocked = useLockStore((s) => s.unlocked)
  const unlockItems = useLockStore((s) => s.unlockItems)
  const transcodePreference = useSettingsStore((s) => s.transcodePreference)
  const slideshowIntervalSec = useSettingsStore((s) => s.slideshowIntervalSec)
  const setCover = useAlbumPrefsStore((s) => s.setCover)

  const [showInfo, setShowInfo] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [showAddToAlbum, setShowAddToAlbum] = useState(false)
  const [slideshowOn, setSlideshowOn] = useState(false)
  // Rename state is tied to the item it was started on, so stepping to another item ends it.
  const [renamingId, setRenamingId] = useState<string | null>(null)
  const [titleDraft, setTitleDraft] = useState('')
  const renameActive = useRef(false)
  const [localEdits, setLocalEdits] = useState<Record<string, Partial<MediaItem>>>({})
  const [single, setSingle] = useState<SingleItem | null>(null)

  const containerRef = useRef<HTMLDivElement>(null)

  // List mode when the item is in the list handed down by the page underneath; otherwise
  // (refresh, shared link, or the list is still loading) fetch just this item and show it alone.
  const listIndex = ctx ? ctx.items.findIndex((i) => i.id === itemId) : -1
  const inList = listIndex >= 0

  useEffect(() => {
    if (inList || !server || !sectionKey || !itemId) return
    if (single?.id === itemId) return
    let cancelled = false
    fetchMetadata(server, sectionKey, itemId)
      .then((found) => !cancelled && setSingle({ id: itemId, item: found, error: found ? null : 'This item could not be found.' }))
      .catch((e) => !cancelled && setSingle({ id: itemId, item: null, error: errorMessage(e, 'Could not load this item.') }))
    return () => {
      cancelled = true
    }
  }, [inList, server, sectionKey, itemId, single?.id])

  const baseItem = inList ? ctx!.items[listIndex] : single?.id === itemId ? single.item : null
  const item = useMemo(() => (baseItem ? { ...baseItem, ...localEdits[baseItem.id] } : null), [baseItem, localEdits])
  const items = useMemo(() => (inList ? ctx!.items : item ? [item] : []), [inList, ctx, item])
  const index = inList ? listIndex : 0
  const basePath = ctx?.basePath ?? '/photos'
  const loadError = !inList && single?.id === itemId ? single.error : null

  const itemLocked = !!item && (lockedItemIds.includes(item.id) || (!!item.albumId && lockedAlbumIds.includes(item.albumId)))
  const dialogOpen = showShare || confirmDelete || showAddToAlbum

  const close = useCallback(() => {
    // Back when the viewer was opened from inside the app, so the list page is restored as it
    // was; otherwise (a shared link opened fresh) go to the list page instead of leaving the app.
    const historyIndex = (window.history.state as { idx?: number } | null)?.idx ?? 0
    if (historyIndex > 0) navigate(-1)
    else navigate(basePath, { replace: true })
  }, [navigate, basePath])

  const goTo = useCallback(
    (next: number) => {
      if (!inList || next < 0 || next >= items.length) return
      navigate(viewerPath(basePath, items[next].id), { replace: true })
    },
    [inList, items, navigate, basePath],
  )
  const next = useCallback(() => goTo(index + 1), [goTo, index])
  const prev = useCallback(() => goTo(index - 1), [goTo, index])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      // Skip keys already handled (the video player seeks with the arrows), keys typed into a
      // field (rename, tags, sliders), and anything while a dialog is open (it owns Escape).
      if (e.defaultPrevented || isEditableTarget(e.target) || dialogOpen) return
      if (e.key === 'ArrowRight') next()
      else if (e.key === 'ArrowLeft') prev()
      else if (e.key === 'Escape') close()
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [next, prev, close, dialogOpen])

  // The list page stays mounted underneath, so take focus from the grid cell that opened the
  // viewer (otherwise the grid's own arrow handling eats the keys) and hand it back on close.
  useEffect(() => {
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    containerRef.current?.focus({ preventScroll: true })
    return () => opener?.focus({ preventScroll: true })
  }, [])

  const advanceRef = useRef<() => void>(() => {})
  useEffect(() => {
    advanceRef.current = () => goTo(index + 1 >= items.length ? 0 : index + 1)
  }, [goTo, index, items.length])
  useEffect(() => {
    if (!slideshowOn) return
    const id = setInterval(() => advanceRef.current(), slideshowIntervalSec * 1000)
    return () => clearInterval(id)
  }, [slideshowOn, slideshowIntervalSec])

  // Preload neighbours at the size the viewer displays them, so the browser cache hits.
  useEffect(() => {
    if (!server) return
    ;[index - 1, index + 1].forEach((i) => {
      const neighbour = items[i]
      if (neighbour && neighbour.kind === 'photo') {
        const img = new Image()
        img.src = buildThumbUrl(server, neighbour.thumbPath, VIEWER_IMAGE_SIZE, VIEWER_IMAGE_SIZE)
      }
    })
  }, [index, items, server])

  const plexLink = useMemo(() => (machineId && item ? buildPlexWebLink(machineId, item.id) : ''), [machineId, item])

  const applyEdit = (id: string, patch: Partial<MediaItem>) => {
    setLocalEdits((p) => ({ ...p, [id]: { ...p[id], ...patch } }))
    ctx?.onItemChanged?.(id, patch)
  }

  if (!server || !item) {
    return (
      <div style={OVERLAY_STYLE}>
        <div style={{ padding: '10px 14px' }}>
          <button className="btn" onClick={close} aria-label="Close viewer">
            <Icon name="close" size={18} />
          </button>
        </div>
        <div className="center-screen" style={{ flex: 1 }}>{loadError ? <p>{loadError}</p> : <div className="spinner" />}</div>
      </div>
    )
  }

  if (itemLocked && !unlocked) {
    return (
      <div style={{ ...OVERLAY_STYLE, background: 'var(--bg)', color: 'var(--text)' }}>
        <div style={{ padding: '10px 14px' }}>
          <button className="btn" onClick={close} aria-label="Close viewer">
            <Icon name="close" size={18} />
          </button>
        </div>
        <PasscodeGate message="This item is locked. Enter your passcode to view it." />
      </div>
    )
  }

  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen()
    else void containerRef.current?.requestFullscreen()
  }

  const metadataType = item.kind === 'video' ? '12' : '13'

  const handleFavourite = async () => {
    const makeFavourite = !item.favourite
    applyEdit(item.id, { favourite: makeFavourite })
    try {
      await rateItem(server, item.id, makeFavourite)
    } catch (e) {
      applyEdit(item.id, { favourite: !makeFavourite })
      showToast(errorMessage(e, 'Could not update the favourite.'))
    }
  }

  const handleDelete = async () => {
    try {
      await deleteItem(server, item.id)
    } catch (e) {
      setConfirmDelete(false)
      showToast(errorMessage(e, 'Could not delete this item.'))
      return
    }
    setConfirmDelete(false)
    const neighbour = inList ? (items[index + 1] ?? items[index - 1]) : undefined
    if (neighbour) navigate(viewerPath(basePath, neighbour.id), { replace: true })
    else close()
    ctx?.onItemsRemoved?.([item.id])
  }

  const handleLockToggle = () => {
    if (lockedItemIds.includes(item.id)) {
      if (!unlockItems([item.id])) showToast('Unlock on the Locked page first.', 'info')
      return
    }
    if (lockItemsOrExplain([item.id])) {
      // The item is now hidden from the list underneath, so leave it.
      close()
    }
  }

  const startRename = () => {
    setTitleDraft(item.title)
    setRenamingId(item.id)
    renameActive.current = true
  }

  const cancelRename = () => {
    renameActive.current = false
    setRenamingId(null)
  }

  const handleRename = async () => {
    // Enter and the blur that follows both land here; only the first one counts.
    if (!renameActive.current) return
    renameActive.current = false
    setRenamingId(null)
    const title = titleDraft.trim()
    if (!title || title === item.title) return
    const previous = item.title
    applyEdit(item.id, { title })
    try {
      await editMetadata(server, { sectionKey: item.sectionKey, ratingKey: item.id, type: metadataType, title })
    } catch (e) {
      applyEdit(item.id, { title: previous })
      showToast(errorMessage(e, 'Could not rename this item.'))
    }
  }

  const handleSetAsCover = async () => {
    if (!item.albumId) return
    setCover(item.albumId, item.id)
    try {
      // Token free, server relative path: Plex stores this value.
      await setAlbumCover(server, item.albumId, item.thumbPath)
    } catch {
      /* local override still applies even if the server call is unsupported */
    }
  }

  const saveMetadata = async (patch: Partial<MediaItem>, options: { summary?: string; addTags?: string[]; removeTags?: string[] }) => {
    const before: Partial<MediaItem> = { summary: item.summary, tags: item.tags }
    applyEdit(item.id, patch)
    try {
      await editMetadata(server, { sectionKey: item.sectionKey, ratingKey: item.id, type: metadataType, ...options })
    } catch (e) {
      applyEdit(item.id, before)
      showToast(errorMessage(e, 'Could not save the change.'))
    }
  }

  const renaming = renamingId === item.id

  return (
    <div ref={containerRef} tabIndex={-1} style={OVERLAY_STYLE}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'rgba(0,0,0,0.6)', gap: 8 }}>
        <button className="btn" onClick={close} aria-label="Close viewer">
          <Icon name="close" size={18} />
        </button>
        {renaming ? (
          <input
            className="input"
            style={{ maxWidth: 300 }}
            value={titleDraft}
            aria-label="Title"
            onChange={(e) => setTitleDraft(e.target.value)}
            onBlur={() => void handleRename()}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void handleRename()
              else if (e.key === 'Escape') {
                e.stopPropagation()
                cancelRename()
              }
            }}
            autoFocus
          />
        ) : (
          <strong onDoubleClick={startRename} style={{ cursor: 'text' }} title="Double click to rename">
            {item.title}
          </strong>
        )}
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          <button className="btn" onClick={() => void handleFavourite()} aria-label={item.favourite ? 'Remove from favourites' : 'Favourite'} aria-pressed={item.favourite}>
            <Icon name={item.favourite ? 'heartFilled' : 'heart'} size={18} />
          </button>
          {item.kind === 'photo' && (
            <button className="btn" onClick={() => navigate(`/edit/${encodeURIComponent(item.id)}`)} aria-label="Edit">
              <Icon name="edit" size={18} />
            </button>
          )}
          <button className="btn" onClick={handleLockToggle} aria-label={lockedItemIds.includes(item.id) ? 'Unlock' : 'Lock'}>
            <Icon name={lockedItemIds.includes(item.id) ? 'unlock' : 'lock'} size={18} />
          </button>
          <button className="btn" onClick={() => setShowShare(true)} aria-label="Share">
            <Icon name="share" size={18} />
          </button>
          <button className="btn" onClick={() => downloadUrl(buildDownloadUrl(server, item.partKey), item.title)} aria-label="Download">
            <Icon name="download" size={18} />
          </button>
          <button className="btn" onClick={() => setShowAddToAlbum(true)} aria-label="Add to album">
            <Icon name="plus" size={18} /> Add to album
          </button>
          {item.albumId && (
            <button className="btn" onClick={() => void handleSetAsCover()} aria-label="Set as album cover">
              Cover
            </button>
          )}
          <button className="btn" onClick={() => setShowInfo((v) => !v)} aria-label="Info">
            <Icon name="info" size={18} />
          </button>
          {inList && items.length > 1 && (
            <button className={`btn${slideshowOn ? ' btn-primary' : ''}`} onClick={() => setSlideshowOn((v) => !v)} aria-label="Slideshow" aria-pressed={slideshowOn}>
              Slideshow
            </button>
          )}
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
          <ZoomableImage key={item.id} src={buildThumbUrl(server, item.thumbPath, VIEWER_IMAGE_SIZE, VIEWER_IMAGE_SIZE)} alt={item.title} onSwipeLeft={next} onSwipeRight={prev} />
        ) : (
          <VideoPlayer key={item.id} item={item} server={server} transcodePreference={transcodePreference} />
        )}

        {items.length > 1 && (
          <>
            <button className="btn" onClick={prev} aria-label="Previous" style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }} disabled={index === 0}>
              <Icon name="arrowLeft" size={18} />
            </button>
            <button className="btn" onClick={next} aria-label="Next" style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)' }} disabled={index === items.length - 1}>
              <Icon name="arrowRight" size={18} />
            </button>
          </>
        )}

        {showInfo && (
          <InfoPanel
            key={item.id}
            item={item}
            onClose={() => setShowInfo(false)}
            onSaveSummary={(summary) => void saveMetadata({ summary }, { summary })}
            onSaveTags={(tags) => {
              const addTags = tags.filter((t) => !item.tags.includes(t))
              const removeTags = item.tags.filter((t) => !tags.includes(t))
              void saveMetadata({ tags }, { addTags, removeTags })
            }}
          />
        )}
      </div>

      {showShare && (
        <ShareSheet title={item.title} link={plexLink || window.location.href} fileUrl={buildDownloadUrl(server, item.partKey)} fileName={item.title} onClose={() => setShowShare(false)} />
      )}
      {confirmDelete && (
        <ConfirmDialog title="Delete item" message="This deletes the item from Plex permanently. This cannot be undone." confirmLabel="Delete" danger onConfirm={() => void handleDelete()} onCancel={() => setConfirmDelete(false)} />
      )}
      {showAddToAlbum && <AddToAlbumDialog itemIds={[item.id]} onClose={() => setShowAddToAlbum(false)} />}
    </div>
  )
}
