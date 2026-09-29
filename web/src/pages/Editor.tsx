import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { fetchMetadata } from '../plex/api'
import { buildOriginalUrl } from '../plex/urls'
import type { MediaItem } from '../plex/model'
import { CropOverlay } from '../components/CropOverlay'
import { MarkupCanvas } from '../components/MarkupCanvas'
import { Icon } from '../components/Icon'
import { AdjustPanel } from '../components/editor/AdjustPanel'
import { FiltersPanel } from '../components/editor/FiltersPanel'
import { CropPanel } from '../components/editor/CropPanel'
import { MarkupPanel } from '../components/editor/MarkupPanel'
import { FramesPanel } from '../components/editor/FramesPanel'
import { ExportPanel, type ExportFormat, type ExportSize } from '../components/editor/ExportPanel'
import {
  DEFAULT_ADJUSTMENTS,
  applyPixelPipeline,
  buildCssFilter,
  clampAdjustments,
  composeAdjustments,
  computeAutoEnhanceAdjustments,
  presetExtraCss,
  type Adjustments,
  type FilterPreset,
} from '../lib/imageFilters'
import { FULL_CROP, clampCropToBounds, computeStraightenSafeRect, cropRectForRatio, rotatedBoundingBox, type CropRect } from '../lib/crop'
import { applyBrushRegions, renderMarkupLayer, type MarkupElement, type MarkupTool } from '../lib/markup'
import { DEFAULT_FRAME_SETTINGS, isDefaultFrame, renderFramed, type FrameSettings } from '../lib/frames'
import {
  copyEditsToClipboard,
  deletePreset,
  hasClipboardEdits,
  loadPresets,
  pasteEditsFromClipboard,
  savePreset,
  type EditorPreset,
} from '../lib/editorPresets'
import { exportFileName, formatShortDate } from '../lib/format'
import { downloadUrl } from '../lib/download'
import { canShareFiles, shareFiles } from '../lib/share'

type EditorTab = 'adjust' | 'filters' | 'crop' | 'markup' | 'frames' | 'export'

interface EditState {
  rotation: 0 | 90 | 180 | 270
  flipH: boolean
  flipV: boolean
  straighten: number
  crop: CropRect
  adjustments: Adjustments
  filterPreset: FilterPreset
  filterStrength: number
  markup: MarkupElement[]
  frame: FrameSettings
}

const INITIAL_STATE: EditState = {
  rotation: 0,
  flipH: false,
  flipV: false,
  straighten: 0,
  crop: { ...FULL_CROP },
  adjustments: DEFAULT_ADJUSTMENTS,
  filterPreset: 'Original',
  filterStrength: 100,
  markup: [],
  frame: DEFAULT_FRAME_SETTINGS,
}

const TABS: { id: EditorTab; label: string; icon: 'sliders' | 'colorSwatch' | 'crop' | 'pen' | 'frame' | 'download' }[] = [
  { id: 'adjust', label: 'Adjust', icon: 'sliders' },
  { id: 'filters', label: 'Filters', icon: 'colorSwatch' },
  { id: 'crop', label: 'Crop', icon: 'crop' },
  { id: 'markup', label: 'Markup', icon: 'pen' },
  { id: 'frames', label: 'Frames', icon: 'frame' },
  { id: 'export', label: 'Export', icon: 'download' },
]

const TEXT_BG_COLOR = 'rgba(0,0,0,0.55)'

/** Pure render pipeline: rotate/flip -> straighten (auto-cropped) -> crop -> CSS filters -> pixel pipeline. */
function renderContent(img: HTMLImageElement, edit: EditState, targetCanvas: HTMLCanvasElement, maxDim: number, seed: number): void {
  const rotated90 = edit.rotation === 90 || edit.rotation === 270
  const naturalW = img.naturalWidth
  const naturalH = img.naturalHeight
  const baseW = rotated90 ? naturalH : naturalW
  const baseH = rotated90 ? naturalW : naturalH

  const quarterCanvas = document.createElement('canvas')
  quarterCanvas.width = Math.max(1, baseW)
  quarterCanvas.height = Math.max(1, baseH)
  const qctx = quarterCanvas.getContext('2d')
  if (!qctx) return
  qctx.save()
  qctx.translate(baseW / 2, baseH / 2)
  qctx.rotate((edit.rotation * Math.PI) / 180)
  qctx.scale(edit.flipH ? -1 : 1, edit.flipV ? -1 : 1)
  qctx.drawImage(img, -naturalW / 2, -naturalH / 2)
  qctx.restore()

  let workCanvas: HTMLCanvasElement = quarterCanvas
  if (edit.straighten !== 0) {
    const grown = rotatedBoundingBox(baseW, baseH, edit.straighten)
    const growW = Math.max(1, Math.round(grown.w))
    const growH = Math.max(1, Math.round(grown.h))
    const straightenCanvas = document.createElement('canvas')
    straightenCanvas.width = growW
    straightenCanvas.height = growH
    const sctx = straightenCanvas.getContext('2d')
    if (!sctx) return
    sctx.save()
    sctx.translate(growW / 2, growH / 2)
    sctx.rotate((edit.straighten * Math.PI) / 180)
    sctx.drawImage(quarterCanvas, -baseW / 2, -baseH / 2)
    sctx.restore()
    workCanvas = straightenCanvas
  }

  const cropX = edit.crop.x * workCanvas.width
  const cropY = edit.crop.y * workCanvas.height
  const cropW = edit.crop.w * workCanvas.width
  const cropH = edit.crop.h * workCanvas.height
  const scale = Math.min(1, maxDim / Math.max(cropW, cropH))
  const outW = Math.max(1, Math.round(cropW * scale))
  const outH = Math.max(1, Math.round(cropH * scale))
  targetCanvas.width = outW
  targetCanvas.height = outH
  const ctx = targetCanvas.getContext('2d')
  if (!ctx) return

  const effective = composeAdjustments(edit.filterPreset, edit.filterStrength, edit.adjustments)
  ctx.filter = buildCssFilter(effective, presetExtraCss(edit.filterPreset))
  ctx.drawImage(workCanvas, cropX, cropY, cropW, cropH, 0, 0, outW, outH)
  ctx.filter = 'none'

  try {
    const imageData = ctx.getImageData(0, 0, outW, outH)
    applyPixelPipeline(imageData, effective, seed)
    ctx.putImageData(imageData, 0, 0)
  } catch {
    // Cross-origin canvases can throw on getImageData; the CSS-only adjustments above still apply.
  }
}

function resolveMaxDim(size: ExportSize): number {
  return size === 'original' ? 4096 : size
}

export function Editor(): React.ReactElement {
  const { id } = useParams()
  const navigate = useNavigate()
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)

  const [item, setItem] = useState<MediaItem | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [past, setPast] = useState<EditState[]>([])
  const [present, setPresent] = useState<EditState>(INITIAL_STATE)
  const [future, setFuture] = useState<EditState[]>([])
  const dragBaselineRef = useRef<EditState | null>(null)

  const [activeTab, setActiveTab] = useState<EditorTab>('adjust')
  const [ratioValue, setRatioValue] = useState<number | null>(null)
  const [ratioLabel, setRatioLabel] = useState<string | null>(null)

  const [imgLoaded, setImgLoaded] = useState(false)
  const [thumbSource, setThumbSource] = useState<HTMLCanvasElement | null>(null)
  const imgRef = useRef<HTMLImageElement | null>(null)
  const previewCanvasRef = useRef<HTMLCanvasElement>(null)
  const grainSeedRef = useRef<number>(Math.floor(Math.random() * 1e9))

  const [holding, setHolding] = useState(false)
  const [toggled, setToggled] = useState(false)
  const showOriginal = holding || toggled

  const [markupTool, setMarkupTool] = useState<MarkupTool>('pen')
  const [markupColor, setMarkupColor] = useState('#e5a00d')
  const [markupBrushSize, setMarkupBrushSize] = useState(0.02)
  const [textColor, setTextColor] = useState('#ffffff')
  const [textBackground, setTextBackground] = useState(true)
  const [textSize, setTextSize] = useState(0.06)
  const [stickerEmoji, setStickerEmoji] = useState('⭐')
  const [stickerSize, setStickerSize] = useState(0.08)
  const [markupSelectedId, setMarkupSelectedId] = useState<string | null>(null)

  const [savedPresets, setSavedPresets] = useState<EditorPreset[]>(() => loadPresets())
  const [clipboardAvailable, setClipboardAvailable] = useState(() => hasClipboardEdits())
  const [copiedMessage, setCopiedMessage] = useState<string | null>(null)

  const [exportFormat, setExportFormat] = useState<ExportFormat>('jpeg')
  const [exportQuality, setExportQuality] = useState(0.92)
  const [exportSize, setExportSize] = useState<ExportSize>('original')
  const [exporting, setExporting] = useState(false)
  const [shareSupported] = useState(() => typeof navigator !== 'undefined' && typeof navigator.share === 'function')

  useEffect(() => {
    if (!server || !sectionKey || !id) return
    fetchMetadata(server, sectionKey, id)
      .then(setItem)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load photo.'))
  }, [server, sectionKey, id])

  useEffect(() => {
    if (!server || !item) return
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      imgRef.current = img
      setImgLoaded(true)
      const size = 240
      const canvas = document.createElement('canvas')
      canvas.width = size
      canvas.height = size
      const ctx = canvas.getContext('2d')
      if (ctx) {
        const srcSize = Math.min(img.naturalWidth, img.naturalHeight)
        const sx = (img.naturalWidth - srcSize) / 2
        const sy = (img.naturalHeight - srcSize) / 2
        ctx.drawImage(img, sx, sy, srcSize, srcSize, 0, 0, size, size)
        setThumbSource(canvas)
      }
    }
    img.onerror = () => setError('Could not load the original image for editing.')
    img.src = buildOriginalUrl(server, item.partKey)
  }, [server, item])

  const update = useCallback((patch: Partial<EditState>) => {
    dragBaselineRef.current = null
    setPast((p) => [...p, present])
    setFuture([])
    setPresent((prev) => ({ ...prev, ...patch }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [present])

  const patchLive = useCallback((patch: Partial<EditState>) => {
    setPresent((prev) => {
      if (dragBaselineRef.current === null) dragBaselineRef.current = prev
      return { ...prev, ...patch }
    })
  }, [])

  const commitDrag = useCallback(() => {
    if (dragBaselineRef.current) {
      const baseline = dragBaselineRef.current
      setPast((p) => [...p, baseline])
      setFuture([])
      dragBaselineRef.current = null
    }
  }, [])

  const undo = useCallback(() => {
    setPast((p) => {
      if (p.length === 0) return p
      const prevState = p[p.length - 1]
      setFuture((f) => [present, ...f])
      setPresent(prevState)
      return p.slice(0, -1)
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [present])

  const redo = useCallback(() => {
    setFuture((f) => {
      if (f.length === 0) return f
      const [next, ...rest] = f
      setPast((p) => [...p, present])
      setPresent(next)
      return rest
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [present])

  const revert = useCallback(() => {
    if (!window.confirm('Revert all edits and go back to the original photo?')) return
    dragBaselineRef.current = null
    setPast((p) => [...p, present])
    setFuture([])
    setPresent(INITIAL_STATE)
    setRatioValue(null)
    setRatioLabel(null)
    setMarkupSelectedId(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [present])

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) return
      const mod = e.ctrlKey || e.metaKey
      if (mod && !e.shiftKey && e.key.toLowerCase() === 'z') {
        e.preventDefault()
        undo()
      } else if (mod && e.shiftKey && e.key.toLowerCase() === 'z') {
        e.preventDefault()
        redo()
      } else if (e.key === 'Escape') {
        setMarkupSelectedId(null)
        setToggled(false)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [undo, redo])

  useEffect(() => {
    const canvas = previewCanvasRef.current
    const img = imgRef.current
    if (!imgLoaded || !canvas || !img) return
    if (showOriginal) {
      const maxDim = 1280
      const scale = Math.min(1, maxDim / Math.max(img.naturalWidth, img.naturalHeight))
      const w = Math.max(1, Math.round(img.naturalWidth * scale))
      const h = Math.max(1, Math.round(img.naturalHeight * scale))
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      ctx?.drawImage(img, 0, 0, w, h)
    } else {
      renderContent(img, present, canvas, 1280, grainSeedRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [imgLoaded, showOriginal, present.rotation, present.flipH, present.flipV, present.straighten, present.crop, present.adjustments, present.filterPreset, present.filterStrength])

  const imgAspect = imgLoaded && imgRef.current ? imgRef.current.naturalWidth / imgRef.current.naturalHeight : null

  function workingDims(): { w: number; h: number } {
    const img = imgRef.current
    if (!img) return { w: 1, h: 1 }
    const rotated90 = present.rotation === 90 || present.rotation === 270
    const baseW = rotated90 ? img.naturalHeight : img.naturalWidth
    const baseH = rotated90 ? img.naturalWidth : img.naturalHeight
    if (present.straighten === 0) return { w: baseW, h: baseH }
    const grown = rotatedBoundingBox(baseW, baseH, present.straighten)
    return { w: grown.w, h: grown.h }
  }

  function cropSafeBounds(): CropRect {
    const img = imgRef.current
    if (!img) return FULL_CROP
    const rotated90 = present.rotation === 90 || present.rotation === 270
    const baseW = rotated90 ? img.naturalHeight : img.naturalWidth
    const baseH = rotated90 ? img.naturalWidth : img.naturalHeight
    return computeStraightenSafeRect(baseW, baseH, present.straighten)
  }

  const handleRotate = () => {
    setRatioValue(null)
    setRatioLabel(null)
    update({ rotation: (((present.rotation + 90) % 360) as EditState['rotation']), crop: { ...FULL_CROP }, straighten: 0 })
  }
  const handleFlipH = () => update({ flipH: !present.flipH })
  const handleFlipV = () => update({ flipV: !present.flipV })

  const handleStraightenChange = (v: number) => {
    const img = imgRef.current
    if (!img) {
      patchLive({ straighten: v })
      return
    }
    const rotated90 = present.rotation === 90 || present.rotation === 270
    const baseW = rotated90 ? img.naturalHeight : img.naturalWidth
    const baseH = rotated90 ? img.naturalWidth : img.naturalHeight
    patchLive({ straighten: v, crop: computeStraightenSafeRect(baseW, baseH, v) })
  }

  const handleRatioChange = (value: number | null, label: string) => {
    setRatioValue(value)
    setRatioLabel(label)
    if (value === null) return
    const dims = workingDims()
    const rect = clampCropToBounds(cropRectForRatio(present.crop, value, dims.w, dims.h), cropSafeBounds())
    update({ crop: rect })
  }

  const handleAutoEnhance = () => {
    const canvas = previewCanvasRef.current
    if (!canvas) return
    try {
      const ctx = canvas.getContext('2d')
      if (!ctx) return
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
      const delta = computeAutoEnhanceAdjustments(imageData)
      update({ adjustments: clampAdjustments({ ...present.adjustments, ...delta }) })
    } catch {
      setError('Auto enhance needs pixel access to the image, which this server does not allow.')
    }
  }

  const handleUpdateSelectedMarkup = (patch: { rotation?: number; scale?: number }) => {
    if (!markupSelectedId) return
    update({
      markup: present.markup.map((el) => {
        if (el.id !== markupSelectedId) return el
        if (el.type === 'text') return { ...el, rotation: patch.rotation ?? el.rotation, scale: patch.scale ?? el.scale }
        if (el.type === 'sticker') return { ...el, rotation: patch.rotation ?? el.rotation }
        return el
      }),
    })
  }
  const handleDeleteSelectedMarkup = () => {
    if (!markupSelectedId) return
    update({ markup: present.markup.filter((el) => el.id !== markupSelectedId) })
    setMarkupSelectedId(null)
  }
  const handleClearMarkup = () => {
    update({ markup: [] })
    setMarkupSelectedId(null)
  }

  const handleSavePreset = (name: string) => {
    setSavedPresets(savePreset(name, present.adjustments, present.filterPreset, present.filterStrength))
  }
  const handleApplySavedPreset = (preset: EditorPreset) => {
    update({ adjustments: preset.adjustments, filterPreset: preset.filterPreset, filterStrength: preset.filterStrength })
  }
  const handleDeleteSavedPreset = (presetId: string) => {
    setSavedPresets(deletePreset(presetId))
  }

  const handleCopyEdits = () => {
    copyEditsToClipboard({ adjustments: present.adjustments, filterPreset: present.filterPreset, filterStrength: present.filterStrength, frame: present.frame })
    setClipboardAvailable(true)
    setCopiedMessage('Edits copied. Open another photo and paste them there.')
  }
  const handlePasteEdits = () => {
    const clip = pasteEditsFromClipboard()
    if (!clip) return
    update({ adjustments: clip.adjustments, filterPreset: clip.filterPreset, filterStrength: clip.filterStrength, frame: clip.frame })
    setCopiedMessage('Edits pasted onto this photo.')
  }

  function buildExportCanvas(maxDim: number): HTMLCanvasElement | null {
    const img = imgRef.current
    if (!img) return null
    const content = document.createElement('canvas')
    renderContent(img, present, content, maxDim, grainSeedRef.current)
    if (present.markup.length > 0) {
      const baseSnapshot = document.createElement('canvas')
      baseSnapshot.width = content.width
      baseSnapshot.height = content.height
      baseSnapshot.getContext('2d')?.drawImage(content, 0, 0)
      const ctx = content.getContext('2d')
      if (ctx) {
        applyBrushRegions(ctx, present.markup, baseSnapshot)
        const layer = renderMarkupLayer(present.markup, content.width, content.height)
        ctx.drawImage(layer, 0, 0)
      }
    }
    if (isDefaultFrame(present.frame)) return content
    const takenAt = item?.takenAt
    const dateLabel = present.frame.dateStamp && takenAt ? formatShortDate(takenAt) : null
    return renderFramed(content, present.frame, dateLabel)
  }

  const mimeFor = (format: ExportFormat) => (format === 'jpeg' ? 'image/jpeg' : format === 'png' ? 'image/png' : 'image/webp')
  const extFor = (format: ExportFormat) => (format === 'jpeg' ? 'jpg' : format)

  const handleDownload = async () => {
    const canvas = buildExportCanvas(resolveMaxDim(exportSize))
    if (!canvas) return
    setExporting(true)
    try {
      const blob: Blob | null = await new Promise((resolve) => canvas.toBlob(resolve, mimeFor(exportFormat), exportFormat === 'png' ? undefined : exportQuality))
      if (!blob) throw new Error('Export failed.')
      const filename = exportFileName().replace(/\.jpg$/, `.${extFor(exportFormat)}`)
      const url = URL.createObjectURL(blob)
      downloadUrl(url, filename)
      URL.revokeObjectURL(url)
    } catch {
      setError('Export failed. This can happen if the Plex server does not allow cross-origin image access.')
    } finally {
      setExporting(false)
    }
  }

  const handleShare = async () => {
    const canvas = buildExportCanvas(resolveMaxDim(exportSize))
    if (!canvas) return
    setExporting(true)
    try {
      const blob: Blob | null = await new Promise((resolve) => canvas.toBlob(resolve, mimeFor(exportFormat), exportFormat === 'png' ? undefined : exportQuality))
      if (!blob) return
      const file = new File([blob], exportFileName().replace(/\.jpg$/, `.${extFor(exportFormat)}`), { type: mimeFor(exportFormat) })
      if (await canShareFiles([file])) {
        await shareFiles([file], item?.title ?? 'Photo')
      } else {
        setError('Sharing files is not supported in this browser; use Download instead.')
      }
    } finally {
      setExporting(false)
    }
  }

  if (error) {
    return (
      <div className="page">
        <p style={{ color: 'var(--danger)' }}>{error}</p>
        <button className="btn" onClick={() => navigate(-1)}>
          Back
        </button>
      </div>
    )
  }

  if (!item || !server) {
    return (
      <div className="center-screen">
        <div className="spinner" />
      </div>
    )
  }

  const currentItem = item
  const markupSelected = present.markup.find((el) => el.id === markupSelectedId) ?? null
  const compareHoldProps =
    activeTab === 'crop' || activeTab === 'markup'
      ? {}
      : {
          onPointerDown: () => setHolding(true),
          onPointerUp: () => setHolding(false),
          onPointerLeave: () => setHolding(false),
          onPointerCancel: () => setHolding(false),
        }

  function renderTabPanel(): React.ReactElement {
    switch (activeTab) {
      case 'adjust':
        return <AdjustPanel adjustments={present.adjustments} onLiveChange={(a) => patchLive({ adjustments: a })} onCommit={commitDrag} onAutoEnhance={handleAutoEnhance} />
      case 'filters':
        return (
          <FiltersPanel
            thumbSource={thumbSource}
            filterPreset={present.filterPreset}
            filterStrength={present.filterStrength}
            onSelectPreset={(p) => update({ filterPreset: p, filterStrength: 100 })}
            onStrengthChange={(v) => patchLive({ filterStrength: v })}
            onStrengthCommit={commitDrag}
            savedPresets={savedPresets}
            onSavePreset={handleSavePreset}
            onApplySavedPreset={handleApplySavedPreset}
            onDeleteSavedPreset={handleDeleteSavedPreset}
          />
        )
      case 'crop':
        return (
          <CropPanel
            ratioLabel={ratioLabel}
            imgAspect={imgAspect}
            onRatioChange={handleRatioChange}
            onRotate={handleRotate}
            onFlipH={handleFlipH}
            onFlipV={handleFlipV}
            straighten={present.straighten}
            onStraightenChange={handleStraightenChange}
            onStraightenCommit={commitDrag}
          />
        )
      case 'markup':
        return (
          <MarkupPanel
            tool={markupTool}
            onToolChange={setMarkupTool}
            color={markupColor}
            onColorChange={setMarkupColor}
            brushSize={markupBrushSize}
            onBrushSizeChange={setMarkupBrushSize}
            textColor={textColor}
            onTextColorChange={setTextColor}
            textBackground={textBackground}
            onTextBackgroundChange={setTextBackground}
            textSize={textSize}
            onTextSizeChange={setTextSize}
            stickerEmoji={stickerEmoji}
            onStickerEmojiChange={setStickerEmoji}
            stickerSize={stickerSize}
            onStickerSizeChange={setStickerSize}
            selectedElement={markupSelected}
            onUpdateSelected={handleUpdateSelectedMarkup}
            onDeleteSelected={handleDeleteSelectedMarkup}
            onClearAll={handleClearMarkup}
            hasElements={present.markup.length > 0}
          />
        )
      case 'frames':
        return <FramesPanel frame={present.frame} onLiveChange={(patch) => patchLive({ frame: { ...present.frame, ...patch } })} onCommit={commitDrag} hasTakenAt={Boolean(currentItem.takenAt)} />
      case 'export':
        return (
          <ExportPanel
            format={exportFormat}
            onFormatChange={setExportFormat}
            quality={exportQuality}
            onQualityChange={setExportQuality}
            size={exportSize}
            onSizeChange={setExportSize}
            onDownload={() => void handleDownload()}
            onShare={() => void handleShare()}
            exporting={exporting}
            canShare={shareSupported}
            onCopyEdits={handleCopyEdits}
            onPasteEdits={handlePasteEdits}
            hasClipboard={clipboardAvailable}
            copiedMessage={copiedMessage}
          />
        )
      default:
        return <></>
    }
  }

  return (
    <div className="page editor-page">
      <div className="page-header">
        <h1 className="page-title">Edit {currentItem.title}</h1>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button type="button" className="btn" onClick={() => navigate(-1)}>
            Cancel
          </button>
          <button type="button" className="btn" onClick={undo} disabled={past.length === 0} aria-label="Undo">
            <Icon name="undo" size={16} />
          </button>
          <button type="button" className="btn" onClick={redo} disabled={future.length === 0} aria-label="Redo">
            <Icon name="redo" size={16} />
          </button>
          <button type="button" className={`btn${toggled ? ' btn-primary' : ''}`} onClick={() => setToggled((t) => !t)} aria-pressed={toggled} aria-label="Toggle before and after view">
            <Icon name="compare" size={16} />
            Compare
          </button>
          <button type="button" className="btn" onClick={revert} aria-label="Revert to original photo">
            <Icon name="revert" size={16} />
            Revert
          </button>
          <button type="button" className="btn btn-primary" onClick={() => void handleDownload()} disabled={exporting} aria-label="Save edited photo">
            <Icon name="save" size={16} />
            {exporting ? 'Saving' : 'Save'}
          </button>
        </div>
      </div>

      <div className="editor-body">
        <div className="editor-stage-wrap" {...compareHoldProps}>
          <div className="editor-stage">
            <canvas ref={previewCanvasRef} aria-label={showOriginal ? 'Original photo' : 'Edited photo preview'} />
            {imgLoaded && activeTab === 'crop' && !showOriginal && (
              <CropOverlay rect={present.crop} bounds={cropSafeBounds()} aspect={ratioValue} onChange={(r) => patchLive({ crop: r })} onCommit={() => commitDrag()} />
            )}
            {imgLoaded && activeTab === 'markup' && !showOriginal && (
              <MarkupCanvas
                elements={present.markup}
                baseCanvas={previewCanvasRef.current}
                tool={markupTool}
                color={markupColor}
                brushSize={markupBrushSize}
                textSettings={{ color: textColor, background: textBackground ? TEXT_BG_COLOR : null, fontSize: textSize }}
                stickerEmoji={stickerEmoji}
                stickerSize={stickerSize}
                selectedId={markupSelectedId}
                onSelect={setMarkupSelectedId}
                onCommit={(elements) => update({ markup: elements })}
              />
            )}
            {showOriginal && (
              <span style={{ position: 'absolute', top: 8, left: 8, background: 'rgba(0,0,0,0.6)', color: '#fff', padding: '2px 8px', borderRadius: 999, fontSize: 11 }}>Original</span>
            )}
          </div>
        </div>

        <div className="editor-panel card">
          <div className="editor-desktop-tabs">
            {TABS.map((t) => (
              <button key={t.id} type="button" className={`chip${activeTab === t.id ? ' active' : ''}`} onClick={() => setActiveTab(t.id)}>
                {t.label}
              </button>
            ))}
          </div>
          {renderTabPanel()}
        </div>
      </div>

      <nav className="editor-tabs" aria-label="Editor tools">
        {TABS.map((t) => (
          <button key={t.id} type="button" className={`editor-tab-btn${activeTab === t.id ? ' active' : ''}`} onClick={() => setActiveTab(t.id)} aria-label={t.label} aria-pressed={activeTab === t.id}>
            <Icon name={t.icon} size={20} />
            <span>{t.label}</span>
          </button>
        ))}
      </nav>
    </div>
  )
}
