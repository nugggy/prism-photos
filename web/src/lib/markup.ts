// Markup (drawing) layer: types plus canvas rendering. Everything is stored in image-normalised
// (0..1) coordinates so it replays identically on the preview canvas and the full size export.

export type MarkupTool = 'pen' | 'highlighter' | 'arrow' | 'rectangle' | 'ellipse' | 'text' | 'sticker' | 'blur' | 'pixelate' | 'eraser'

export interface Point {
  x: number
  y: number
}

interface BaseElement {
  id: string
}

export interface StrokeElement extends BaseElement {
  type: 'pen' | 'highlighter' | 'eraser'
  points: Point[]
  color: string
  size: number // fraction of min(width, height)
}

export interface ShapeElement extends BaseElement {
  type: 'arrow' | 'rectangle' | 'ellipse'
  x1: number
  y1: number
  x2: number
  y2: number
  color: string
  size: number
}

export interface TextElement extends BaseElement {
  type: 'text'
  x: number
  y: number
  text: string
  color: string
  background: string | null
  size: number // fraction of image height
  rotation: number // degrees
  scale: number
}

export interface StickerElement extends BaseElement {
  type: 'sticker'
  x: number
  y: number
  emoji: string
  size: number // fraction of image height
  rotation: number
}

export interface BrushRegionElement extends BaseElement {
  type: 'blur' | 'pixelate'
  points: Point[]
  size: number
}

export type MarkupElement = StrokeElement | ShapeElement | TextElement | StickerElement | BrushRegionElement

export function createMarkupId(): string {
  return `m_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`
}

function strokePath(ctx: CanvasRenderingContext2D, points: Point[], w: number, h: number): void {
  if (points.length === 0) return
  ctx.beginPath()
  points.forEach((p, i) => {
    const x = p.x * w
    const y = p.y * h
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  })
  if (points.length === 1) {
    const p = points[0]
    ctx.moveTo(p.x * w, p.y * h)
    ctx.lineTo(p.x * w + 0.01, p.y * h)
  }
}

function drawStrokeElement(ctx: CanvasRenderingContext2D, el: StrokeElement, w: number, h: number): void {
  const minDim = Math.min(w, h)
  ctx.save()
  if (el.type === 'eraser') {
    ctx.globalCompositeOperation = 'destination-out'
    ctx.strokeStyle = 'rgba(0,0,0,1)'
  } else {
    ctx.globalCompositeOperation = 'source-over'
    ctx.strokeStyle = el.color
    ctx.globalAlpha = el.type === 'highlighter' ? 0.4 : 1
  }
  ctx.lineWidth = Math.max(1, el.size * minDim)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  strokePath(ctx, el.points, w, h)
  ctx.stroke()
  ctx.restore()
}

function drawArrow(ctx: CanvasRenderingContext2D, el: ShapeElement, w: number, h: number): void {
  const x1 = el.x1 * w
  const y1 = el.y1 * h
  const x2 = el.x2 * w
  const y2 = el.y2 * h
  const lineWidth = Math.max(1, el.size * Math.min(w, h))
  const angle = Math.atan2(y2 - y1, x2 - x1)
  const headLen = Math.max(10, lineWidth * 4)

  ctx.save()
  ctx.strokeStyle = el.color
  ctx.fillStyle = el.color
  ctx.lineWidth = lineWidth
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(x1, y1)
  ctx.lineTo(x2, y2)
  ctx.stroke()

  ctx.beginPath()
  ctx.moveTo(x2, y2)
  ctx.lineTo(x2 - headLen * Math.cos(angle - Math.PI / 6), y2 - headLen * Math.sin(angle - Math.PI / 6))
  ctx.lineTo(x2 - headLen * Math.cos(angle + Math.PI / 6), y2 - headLen * Math.sin(angle + Math.PI / 6))
  ctx.closePath()
  ctx.fill()
  ctx.restore()
}

function drawRectOrEllipse(ctx: CanvasRenderingContext2D, el: ShapeElement, w: number, h: number): void {
  const x = Math.min(el.x1, el.x2) * w
  const y = Math.min(el.y1, el.y2) * h
  const rw = Math.abs(el.x2 - el.x1) * w
  const rh = Math.abs(el.y2 - el.y1) * h
  ctx.save()
  ctx.strokeStyle = el.color
  ctx.lineWidth = Math.max(1, el.size * Math.min(w, h))
  if (el.type === 'rectangle') {
    ctx.strokeRect(x, y, rw, rh)
  } else {
    ctx.beginPath()
    ctx.ellipse(x + rw / 2, y + rh / 2, Math.abs(rw / 2), Math.abs(rh / 2), 0, 0, Math.PI * 2)
    ctx.stroke()
  }
  ctx.restore()
}

function drawText(ctx: CanvasRenderingContext2D, el: TextElement, w: number, h: number): void {
  const fontSize = Math.max(8, el.size * h * el.scale)
  ctx.save()
  ctx.translate(el.x * w, el.y * h)
  ctx.rotate((el.rotation * Math.PI) / 180)
  ctx.font = `600 ${fontSize}px -apple-system, "Segoe UI", Roboto, sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  const metrics = ctx.measureText(el.text)
  const padX = fontSize * 0.4
  const padY = fontSize * 0.28
  if (el.background) {
    const bw = metrics.width + padX * 2
    const bh = fontSize + padY * 2
    const r = bh / 2
    ctx.fillStyle = el.background
    roundRect(ctx, -bw / 2, -bh / 2, bw, bh, r)
    ctx.fill()
  }
  ctx.fillStyle = el.color
  ctx.fillText(el.text, 0, 0)
  ctx.restore()
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

function drawSticker(ctx: CanvasRenderingContext2D, el: StickerElement, w: number, h: number): void {
  const fontSize = Math.max(8, el.size * h)
  ctx.save()
  ctx.translate(el.x * w, el.y * h)
  ctx.rotate((el.rotation * Math.PI) / 180)
  ctx.font = `${fontSize}px "Apple Color Emoji", "Segoe UI Emoji", sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(el.emoji, 0, 0)
  ctx.restore()
}

/** Renders the strokes/shapes/text/stickers/eraser onto a transparent layer (does not touch pixels). */
export function renderMarkupLayer(elements: MarkupElement[], w: number, h: number): HTMLCanvasElement {
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) return canvas
  for (const el of elements) {
    switch (el.type) {
      case 'pen':
      case 'highlighter':
      case 'eraser':
        drawStrokeElement(ctx, el, w, h)
        break
      case 'arrow':
        drawArrow(ctx, el, w, h)
        break
      case 'rectangle':
      case 'ellipse':
        drawRectOrEllipse(ctx, el, w, h)
        break
      case 'text':
        drawText(ctx, el, w, h)
        break
      case 'sticker':
        drawSticker(ctx, el, w, h)
        break
      case 'blur':
      case 'pixelate':
        break // handled separately, they sample the photo rather than draw on a transparent layer
    }
  }
  return canvas
}

/** Applies blur/pixelate brush strokes directly onto the main canvas, sampling from `base`. */
export function applyBrushRegions(mainCtx: CanvasRenderingContext2D, elements: MarkupElement[], base: HTMLCanvasElement): void {
  const w = base.width
  const h = base.height
  for (const el of elements) {
    if (el.type !== 'blur' && el.type !== 'pixelate') continue
    const sizePx = Math.max(4, el.size * Math.min(w, h))

    const effectCanvas = document.createElement('canvas')
    effectCanvas.width = w
    effectCanvas.height = h
    const ectx = effectCanvas.getContext('2d')
    if (!ectx) continue

    if (el.type === 'pixelate') {
      const block = Math.max(3, Math.round(sizePx / 3))
      const smallW = Math.max(1, Math.round(w / block))
      const smallH = Math.max(1, Math.round(h / block))
      const small = document.createElement('canvas')
      small.width = smallW
      small.height = smallH
      const sctx = small.getContext('2d')
      if (sctx) {
        sctx.imageSmoothingEnabled = false
        sctx.drawImage(base, 0, 0, smallW, smallH)
        ectx.imageSmoothingEnabled = false
        ectx.drawImage(small, 0, 0, w, h)
      }
    } else {
      ectx.filter = `blur(${Math.max(2, sizePx / 2.2)}px)`
      ectx.drawImage(base, 0, 0)
      ectx.filter = 'none'
    }

    const maskCanvas = document.createElement('canvas')
    maskCanvas.width = w
    maskCanvas.height = h
    const mctx = maskCanvas.getContext('2d')
    if (!mctx) continue
    mctx.strokeStyle = '#fff'
    mctx.fillStyle = '#fff'
    mctx.lineWidth = sizePx
    mctx.lineCap = 'round'
    mctx.lineJoin = 'round'
    if (el.points.length <= 1) {
      const p = el.points[0]
      if (p) {
        mctx.beginPath()
        mctx.arc(p.x * w, p.y * h, sizePx / 2, 0, Math.PI * 2)
        mctx.fill()
      }
    } else {
      strokePath(mctx, el.points, w, h)
      mctx.stroke()
    }

    ectx.globalCompositeOperation = 'destination-in'
    ectx.drawImage(maskCanvas, 0, 0)
    ectx.globalCompositeOperation = 'source-over'

    mainCtx.drawImage(effectCanvas, 0, 0)
  }
}

export const MARKUP_COLORS = ['#ffffff', '#e5a00d', '#e5484d', '#3dd68c', '#3d9be5', '#111318', '#ff7ab6', '#ffd23d']

export const STICKER_EMOJIS = ['⭐', '❤️', '🔥', '🎉', '👍', '😀', '😎', '📍', '✅', '☀️', '❄️', '🌙']
