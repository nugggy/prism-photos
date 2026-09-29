import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { fetchMetadata } from '../plex/api'
import { buildOriginalUrl } from '../plex/urls'
import type { MediaItem } from '../plex/model'
import { CropOverlay, type CropRect } from '../components/CropOverlay'
import { applyWarmth, buildCssFilter, DEFAULT_ADJUSTMENTS, FILTER_PRESETS, presetAdjustments, type Adjustments, type FilterPreset } from '../lib/imageFilters'
import { exportFileName } from '../lib/format'
import { canShareFiles, shareFiles } from '../lib/share'
import { Icon } from '../components/Icon'

const RATIOS: { label: string; value: number | null }[] = [
  { label: 'Free', value: null },
  { label: '1:1', value: 1 },
  { label: '4:3', value: 4 / 3 },
  { label: '3:2', value: 3 / 2 },
  { label: '16:9', value: 16 / 9 },
  { label: '9:16', value: 9 / 16 },
]

interface EditState {
  rotation: 0 | 90 | 180 | 270
  flipH: boolean
  flipV: boolean
  crop: CropRect
  adjustments: Adjustments
  preset: FilterPreset
}

const INITIAL_STATE: EditState = {
  rotation: 0,
  flipH: false,
  flipV: false,
  crop: { x: 0, y: 0, w: 1, h: 1 },
  adjustments: DEFAULT_ADJUSTMENTS,
  preset: 'Original',
}

export function Editor(): React.ReactElement {
  const { id } = useParams()
  const navigate = useNavigate()
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)

  const [item, setItem] = useState<MediaItem | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [state, setState] = useState<EditState>(INITIAL_STATE)
  const [history, setHistory] = useState<EditState[]>([])
  const [ratio, setRatio] = useState<number | null>(null)
  const [exporting, setExporting] = useState(false)

  const imgRef = useRef<HTMLImageElement | null>(null)
  const previewCanvasRef = useRef<HTMLCanvasElement>(null)
  const [imgLoaded, setImgLoaded] = useState(false)

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
    }
    img.onerror = () => setError('Could not load the original image for editing.')
    img.src = buildOriginalUrl(server, item.partKey)
  }, [server, item])

  const pushHistory = useCallback((next: EditState) => {
    setHistory((h) => [...h, next])
  }, [])

  const update = useCallback(
    (patch: Partial<EditState>) => {
      setState((prev) => {
        pushHistory(prev)
        return { ...prev, ...patch }
      })
    },
    [pushHistory],
  )

  const undo = useCallback(() => {
    setHistory((h) => {
      if (h.length === 0) return h
      const last = h[h.length - 1]
      setState(last)
      return h.slice(0, -1)
    })
  }, [])

  const reset = useCallback(() => {
    pushHistory(state)
    setState(INITIAL_STATE)
    setRatio(null)
  }, [state, pushHistory])

  const render = useCallback(
    (targetCanvas: HTMLCanvasElement, maxDim: number) => {
      const img = imgRef.current
      if (!img) return
      const rotated90 = state.rotation === 90 || state.rotation === 270
      const baseW = img.naturalWidth
      const baseH = img.naturalHeight

      // Step 1: draw rotated/flipped full image onto an offscreen canvas.
      const rotCanvas = document.createElement('canvas')
      rotCanvas.width = rotated90 ? baseH : baseW
      rotCanvas.height = rotated90 ? baseW : baseH
      const rctx = rotCanvas.getContext('2d')
      if (!rctx) return
      rctx.save()
      rctx.translate(rotCanvas.width / 2, rotCanvas.height / 2)
      rctx.rotate((state.rotation * Math.PI) / 180)
      rctx.scale(state.flipH ? -1 : 1, state.flipV ? -1 : 1)
      rctx.drawImage(img, -baseW / 2, -baseH / 2)
      rctx.restore()

      // Step 2: crop.
      const cropX = state.crop.x * rotCanvas.width
      const cropY = state.crop.y * rotCanvas.height
      const cropW = state.crop.w * rotCanvas.width
      const cropH = state.crop.h * rotCanvas.height

      const scale = Math.min(1, maxDim / Math.max(cropW, cropH))
      const outW = Math.max(1, Math.round(cropW * scale))
      const outH = Math.max(1, Math.round(cropH * scale))
      targetCanvas.width = outW
      targetCanvas.height = outH
      const ctx = targetCanvas.getContext('2d')
      if (!ctx) return
      ctx.filter = buildCssFilter(state.adjustments)
      ctx.drawImage(rotCanvas, cropX, cropY, cropW, cropH, 0, 0, outW, outH)
      ctx.filter = 'none'

      if (state.adjustments.warmth !== 0) {
        const imageData = ctx.getImageData(0, 0, outW, outH)
        applyWarmth(imageData, state.adjustments.warmth)
        ctx.putImageData(imageData, 0, 0)
      }
    },
    [state],
  )

  useEffect(() => {
    if (!imgLoaded || !previewCanvasRef.current) return
    render(previewCanvasRef.current, 1200)
  }, [imgLoaded, render])

  const applyPreset = (preset: FilterPreset) => {
    update({ preset, adjustments: presetAdjustments(preset) })
  }

  const handleExport = async () => {
    if (!imgRef.current) return
    setExporting(true)
    try {
      const canvas = document.createElement('canvas')
      render(canvas, 8000)
      const blob: Blob | null = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
      if (!blob) throw new Error('Export failed.')
      const filename = exportFileName()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch {
      setError('Export failed. This can happen if the Plex server does not allow cross-origin image access.')
    } finally {
      setExporting(false)
    }
  }

  const handleShare = async () => {
    if (!imgRef.current) return
    const canvas = document.createElement('canvas')
    render(canvas, 4000)
    const blob: Blob | null = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
    if (!blob) return
    const file = new File([blob], exportFileName(), { type: 'image/jpeg' })
    if (await canShareFiles([file])) {
      await shareFiles([file], item?.title ?? 'Photo')
    } else {
      setError('Sharing files is not supported in this browser; use Export instead.')
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

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Edit {item.title}</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn" onClick={() => navigate(-1)}>
            Cancel
          </button>
          <button className="btn" onClick={undo} disabled={history.length === 0}>
            Undo
          </button>
          <button className="btn" onClick={reset}>
            Reset
          </button>
          <button className="btn" onClick={() => void handleShare()}>
            Share
          </button>
          <button className="btn btn-primary" onClick={() => void handleExport()} disabled={exporting}>
            {exporting ? 'Exporting…' : 'Export as JPEG'}
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ flex: '1 1 500px', position: 'relative', background: '#000', borderRadius: 8, minHeight: 400, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <canvas ref={previewCanvasRef} style={{ maxWidth: '100%', maxHeight: '70vh' }} />
          {imgLoaded && (
            <CropOverlay rect={state.crop} onChange={(crop) => update({ crop })} aspect={ratio} />
          )}
        </div>

        <div style={{ width: 280 }} className="card">
          <h3 style={{ marginTop: 0, fontSize: 14 }}>Crop</h3>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 14 }}>
            {RATIOS.map((r) => (
              <button key={r.label} className={`chip${ratio === r.value ? ' active' : ''}`} onClick={() => setRatio(r.value)}>
                {r.label}
              </button>
            ))}
          </div>

          <h3 style={{ fontSize: 14 }}>Rotate &amp; flip</h3>
          <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
            <button className="btn" onClick={() => update({ rotation: ((state.rotation + 90) % 360) as EditState['rotation'] })} aria-label="Rotate 90 degrees">
              <Icon name="rotate" size={16} />
            </button>
            <button className="btn" onClick={() => update({ flipH: !state.flipH })} aria-label="Flip horizontal">
              <Icon name="flip" size={16} />
              H
            </button>
            <button className="btn" onClick={() => update({ flipV: !state.flipV })} aria-label="Flip vertical">
              <Icon name="flip" size={16} />
              V
            </button>
          </div>

          <h3 style={{ fontSize: 14 }}>Adjustments</h3>
          <Slider label="Brightness" value={state.adjustments.brightness} onChange={(v) => update({ adjustments: { ...state.adjustments, brightness: v } })} />
          <Slider label="Contrast" value={state.adjustments.contrast} onChange={(v) => update({ adjustments: { ...state.adjustments, contrast: v } })} />
          <Slider label="Saturation" value={state.adjustments.saturation} onChange={(v) => update({ adjustments: { ...state.adjustments, saturation: v } })} />
          <Slider label="Warmth" value={state.adjustments.warmth} onChange={(v) => update({ adjustments: { ...state.adjustments, warmth: v } })} />

          <h3 style={{ fontSize: 14 }}>Filters</h3>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {FILTER_PRESETS.map((p) => (
              <button key={p} className={`chip${state.preset === p ? ' active' : ''}`} onClick={() => applyPreset(p)}>
                {p}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function Slider({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }): React.ReactElement {
  return (
    <div style={{ marginBottom: 8 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }} className="muted">
        <span>{label}</span>
        <span>{value}</span>
      </div>
      <input type="range" min={-100} max={100} value={value} onChange={(e) => onChange(Number(e.target.value))} style={{ width: '100%' }} />
    </div>
  )
}
