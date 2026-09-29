import { useEffect, useRef, useState } from 'react'
import {
  applyBrushRegions,
  createMarkupId,
  renderMarkupLayer,
  type MarkupElement,
  type MarkupTool,
  type Point,
  type StickerElement,
  type TextElement,
} from '../lib/markup'

export interface TextSettings {
  color: string
  background: string | null
  fontSize: number // fraction of image height
}

export interface MarkupCanvasProps {
  elements: MarkupElement[]
  baseCanvas: HTMLCanvasElement | null
  tool: MarkupTool
  color: string
  brushSize: number
  textSettings: TextSettings
  stickerEmoji: string
  stickerSize: number
  selectedId: string | null
  onSelect: (id: string | null) => void
  onCommit: (elements: MarkupElement[]) => void
}

function clamp01(v: number): number {
  return Math.max(0, Math.min(1, v))
}

function isPointElement(el: MarkupElement): el is TextElement | StickerElement {
  return el.type === 'text' || el.type === 'sticker'
}

export function MarkupCanvas(props: MarkupCanvasProps): React.ReactElement {
  const { elements, baseCanvas, tool, color, brushSize, textSettings, stickerEmoji, stickerSize, selectedId, onSelect, onCommit } = props
  const containerRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [draft, setDraft] = useState<MarkupElement | null>(null)
  const [liveElements, setLiveElements] = useState<MarkupElement[] | null>(null)
  const [pendingText, setPendingText] = useState<Point | null>(null)
  const [textValue, setTextValue] = useState('')

  const displayElements = liveElements ?? elements

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !baseCanvas || baseCanvas.width === 0) return
    canvas.width = baseCanvas.width
    canvas.height = baseCanvas.height
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    const all = draft ? [...displayElements, draft] : displayElements
    const brushEls = all.filter((e) => e.type === 'blur' || e.type === 'pixelate')
    if (brushEls.length > 0) applyBrushRegions(ctx, brushEls, baseCanvas)
    const layer = renderMarkupLayer(all, canvas.width, canvas.height)
    ctx.drawImage(layer, 0, 0)

    if (selectedId) {
      const selected = displayElements.find((item) => item.id === selectedId)
      if (selected && isPointElement(selected)) {
        const radius = (selected.type === 'text' ? selected.size * selected.scale * 0.9 : selected.size * 0.6) * canvas.height
        ctx.save()
        ctx.strokeStyle = '#e5a00d'
        ctx.setLineDash([6, 4])
        ctx.lineWidth = 2
        ctx.beginPath()
        ctx.arc(selected.x * canvas.width, selected.y * canvas.height, radius, 0, Math.PI * 2)
        ctx.stroke()
        ctx.restore()
      }
    }
  }, [displayElements, draft, baseCanvas, selectedId])

  function toPoint(e: React.PointerEvent): Point {
    const el = containerRef.current
    if (!el) return { x: 0, y: 0 }
    const r = el.getBoundingClientRect()
    return { x: clamp01((e.clientX - r.left) / r.width), y: clamp01((e.clientY - r.top) / r.height) }
  }

  function hitTestPoint(p: Point): (TextElement | StickerElement) | null {
    let best: (TextElement | StickerElement) | null = null
    let bestDist = Infinity
    for (const el of elements) {
      if (!isPointElement(el)) continue
      const dist = Math.hypot(el.x - p.x, el.y - p.y)
      const threshold = el.type === 'text' ? el.size * el.scale * 0.9 : el.size * 0.6
      if (dist < threshold && dist < bestDist) {
        best = el
        bestDist = dist
      }
    }
    return best
  }

  function handlePointerDown(e: React.PointerEvent): void {
    if (pendingText) return
    const p = toPoint(e)

    if (tool === 'text') {
      const hit = hitTestPoint(p)
      if (hit && hit.type === 'text') {
        onSelect(hit.id)
        beginMoveDrag(hit.id, p)
        return
      }
      onSelect(null)
      setPendingText(p)
      setTextValue('')
      return
    }

    if (tool === 'sticker') {
      const hit = hitTestPoint(p)
      if (hit && hit.type === 'sticker') {
        onSelect(hit.id)
        beginMoveDrag(hit.id, p)
        return
      }
      const sticker: StickerElement = { id: createMarkupId(), type: 'sticker', x: p.x, y: p.y, emoji: stickerEmoji, size: stickerSize, rotation: 0 }
      const next = [...elements, sticker]
      onCommit(next)
      onSelect(sticker.id)
      return
    }

    if (tool === 'pen' || tool === 'highlighter' || tool === 'eraser') {
      setDraft({ id: createMarkupId(), type: tool, points: [p], color, size: brushSize })
      beginStrokeDrag()
      return
    }
    if (tool === 'blur' || tool === 'pixelate') {
      setDraft({ id: createMarkupId(), type: tool, points: [p], size: brushSize })
      beginStrokeDrag()
      return
    }
    if (tool === 'arrow' || tool === 'rectangle' || tool === 'ellipse') {
      setDraft({ id: createMarkupId(), type: tool, x1: p.x, y1: p.y, x2: p.x, y2: p.y, color, size: brushSize })
      beginShapeDrag()
      return
    }
  }

  function beginStrokeDrag(): void {
    const onMove = (ev: PointerEvent) => {
      const el = containerRef.current
      if (!el) return
      const r = el.getBoundingClientRect()
      const p: Point = { x: clamp01((ev.clientX - r.left) / r.width), y: clamp01((ev.clientY - r.top) / r.height) }
      setDraft((prev) => {
        if (!prev || (prev.type !== 'pen' && prev.type !== 'highlighter' && prev.type !== 'eraser' && prev.type !== 'blur' && prev.type !== 'pixelate')) return prev
        return { ...prev, points: [...prev.points, p] }
      })
    }
    const onUp = () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      setDraft((prev) => {
        if (prev) onCommit([...elements, prev])
        return null
      })
    }
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
  }

  function beginShapeDrag(): void {
    const onMove = (ev: PointerEvent) => {
      const el = containerRef.current
      if (!el) return
      const r = el.getBoundingClientRect()
      const p: Point = { x: clamp01((ev.clientX - r.left) / r.width), y: clamp01((ev.clientY - r.top) / r.height) }
      setDraft((prev) => (prev && (prev.type === 'arrow' || prev.type === 'rectangle' || prev.type === 'ellipse') ? { ...prev, x2: p.x, y2: p.y } : prev))
    }
    const onUp = () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      setDraft((prev) => {
        if (prev) onCommit([...elements, prev])
        return null
      })
    }
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
  }

  function beginMoveDrag(id: string, start: Point): void {
    const el = elements.find((x) => x.id === id)
    if (!el || !isPointElement(el)) return
    const offsetX = el.x - start.x
    const offsetY = el.y - start.y
    setLiveElements(elements)
    const onMove = (ev: PointerEvent) => {
      const container = containerRef.current
      if (!container) return
      const r = container.getBoundingClientRect()
      const p: Point = { x: clamp01((ev.clientX - r.left) / r.width), y: clamp01((ev.clientY - r.top) / r.height) }
      setLiveElements((prev) =>
        (prev ?? elements).map((item) => (item.id === id && isPointElement(item) ? { ...item, x: clamp01(p.x + offsetX), y: clamp01(p.y + offsetY) } : item)),
      )
    }
    const onUp = () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      setLiveElements((prev) => {
        if (prev) onCommit(prev)
        return null
      })
    }
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
  }

  function commitPendingText(): void {
    if (!pendingText) return
    const value = textValue.trim()
    if (value) {
      const el: TextElement = {
        id: createMarkupId(),
        type: 'text',
        x: pendingText.x,
        y: pendingText.y,
        text: value,
        color: textSettings.color,
        background: textSettings.background,
        size: textSettings.fontSize,
        rotation: 0,
        scale: 1,
      }
      onCommit([...elements, el])
      onSelect(el.id)
    }
    setPendingText(null)
    setTextValue('')
  }

  return (
    <div
      ref={containerRef}
      onPointerDown={handlePointerDown}
      style={{ position: 'absolute', inset: 0, touchAction: 'none', cursor: tool === 'text' || tool === 'sticker' ? 'pointer' : 'crosshair' }}
    >
      <canvas ref={canvasRef} style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }} />
      {pendingText && (
        <input
          autoFocus
          className="input"
          aria-label="Text overlay content"
          value={textValue}
          onChange={(e) => setTextValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') commitPendingText()
            if (e.key === 'Escape') {
              setPendingText(null)
              setTextValue('')
            }
          }}
          onBlur={commitPendingText}
          style={{
            position: 'absolute',
            left: `${pendingText.x * 100}%`,
            top: `${pendingText.y * 100}%`,
            transform: 'translate(-50%, -50%)',
            width: 160,
            zIndex: 5,
          }}
        />
      )}
    </div>
  )
}
