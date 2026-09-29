// Frame/finishing effects: border, rounded corners, polaroid mat, date stamp, text watermark.

export interface FrameSettings {
  borderThickness: number // 0..100 (fraction of shortest side * 0.08 max)
  borderColor: string
  cornerRadius: number // 0..100 (fraction of shortest side * 0.12 max)
  polaroid: boolean
  dateStamp: boolean
  watermarkText: string
}

export const DEFAULT_FRAME_SETTINGS: FrameSettings = {
  borderThickness: 0,
  borderColor: '#ffffff',
  cornerRadius: 0,
  polaroid: false,
  dateStamp: false,
  watermarkText: '',
}

export function isDefaultFrame(f: FrameSettings): boolean {
  return (
    f.borderThickness === 0 &&
    f.cornerRadius === 0 &&
    !f.polaroid &&
    !f.dateStamp &&
    f.watermarkText.trim() === ''
  )
}

function roundedRectPath(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  const radius = Math.max(0, Math.min(r, w / 2, h / 2))
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

/**
 * Draws `source` onto a new canvas with the requested frame treatment applied. Returns the new
 * canvas (source is left untouched). `dateLabel` should already be formatted (Australian date).
 */
export function renderFramed(source: HTMLCanvasElement, settings: FrameSettings, dateLabel: string | null): HTMLCanvasElement {
  const shortSide = Math.min(source.width, source.height)
  const border = Math.round((settings.borderThickness / 100) * shortSide * 0.08)
  const polaroidBottom = settings.polaroid ? Math.round(shortSide * 0.18) : 0
  const outerBorder = settings.polaroid ? Math.max(border, Math.round(shortSide * 0.03)) : border
  const radius = (settings.cornerRadius / 100) * shortSide * 0.12

  const outW = source.width + outerBorder * 2
  const outH = source.height + outerBorder * 2 + polaroidBottom

  const out = document.createElement('canvas')
  out.width = outW
  out.height = outH
  const ctx = out.getContext('2d')
  if (!ctx) return source

  ctx.fillStyle = settings.borderColor
  ctx.fillRect(0, 0, outW, outH)

  ctx.save()
  roundedRectPath(ctx, outerBorder, outerBorder, source.width, source.height, radius)
  ctx.clip()
  ctx.drawImage(source, outerBorder, outerBorder)
  ctx.restore()

  if (radius > 0 && (outerBorder === 0 || settings.polaroid)) {
    // Round the outer corners too when there's no border to hide the seam.
    ctx.save()
    ctx.globalCompositeOperation = 'destination-in'
    roundedRectPath(ctx, 0, 0, outW, settings.polaroid ? outH : outH, Math.max(radius, settings.polaroid ? 0 : radius))
    ctx.fill()
    ctx.restore()
  }

  if (settings.dateStamp && dateLabel) {
    const fontSize = Math.max(12, Math.round(shortSide * 0.028))
    ctx.save()
    ctx.font = `600 ${fontSize}px "Courier New", monospace`
    ctx.textAlign = 'right'
    ctx.textBaseline = 'bottom'
    const padding = fontSize * 0.9
    const x = outW - outerBorder - padding
    const y = outH - (settings.polaroid ? polaroidBottom : outerBorder) - padding + (settings.polaroid ? polaroidBottom * 0.55 : 0)
    ctx.fillStyle = 'rgba(0,0,0,0.55)'
    ctx.fillText(dateLabel, x + 1, y + 1)
    ctx.fillStyle = settings.polaroid ? '#e5a00d' : '#ffd76b'
    ctx.fillText(dateLabel, x, y)
    ctx.restore()
  }

  if (settings.watermarkText.trim()) {
    const fontSize = Math.max(12, Math.round(shortSide * 0.032))
    ctx.save()
    ctx.font = `600 ${fontSize}px -apple-system, "Segoe UI", Roboto, sans-serif`
    ctx.textAlign = 'left'
    ctx.textBaseline = 'bottom'
    const padding = fontSize * 0.9
    const x = outerBorder + padding
    const y = outH - (settings.polaroid ? polaroidBottom * 0.55 : outerBorder) - padding
    ctx.globalAlpha = 0.85
    ctx.fillStyle = 'rgba(0,0,0,0.55)'
    ctx.fillText(settings.watermarkText, x + 1, y + 1)
    ctx.fillStyle = '#ffffff'
    ctx.fillText(settings.watermarkText, x, y)
    ctx.restore()
  }

  return out
}
