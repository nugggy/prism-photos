import { useCallback, useRef } from 'react'

export interface CropRect {
  x: number // 0..1
  y: number // 0..1
  w: number // 0..1
  h: number // 0..1
}

export interface CropOverlayProps {
  rect: CropRect
  onChange: (rect: CropRect) => void
  aspect: number | null // width/height, or null for free
}

const HANDLES = ['nw', 'ne', 'sw', 'se'] as const

export function CropOverlay({ rect, onChange, aspect }: CropOverlayProps): React.ReactElement {
  const containerRef = useRef<HTMLDivElement>(null)

  const clamp01 = (v: number) => Math.max(0, Math.min(1, v))

  const startDrag = useCallback(
    (handle: (typeof HANDLES)[number] | 'move', e: React.PointerEvent) => {
      e.stopPropagation()
      const container = containerRef.current
      if (!container) return
      const bounds = container.getBoundingClientRect()
      const startRect = { ...rect }
      const startX = e.clientX
      const startY = e.clientY

      const onMove = (ev: PointerEvent) => {
        const dx = (ev.clientX - startX) / bounds.width
        const dy = (ev.clientY - startY) / bounds.height
        let { x, y, w, h } = startRect

        if (handle === 'move') {
          x = clamp01(startRect.x + dx)
          y = clamp01(startRect.y + dy)
          x = Math.min(x, 1 - w)
          y = Math.min(y, 1 - h)
        } else {
          let left = startRect.x
          let top = startRect.y
          let right = startRect.x + startRect.w
          let bottom = startRect.y + startRect.h
          if (handle.includes('w')) left = clamp01(startRect.x + dx)
          if (handle.includes('e')) right = clamp01(startRect.x + startRect.w + dx)
          if (handle.includes('n')) top = clamp01(startRect.y + dy)
          if (handle.includes('s')) bottom = clamp01(startRect.y + startRect.h + dy)

          w = Math.max(0.05, right - left)
          h = Math.max(0.05, bottom - top)
          if (aspect) {
            // Maintain aspect ratio using width as the driver.
            h = w / aspect / (bounds.width / bounds.height)
          }
          x = left
          y = top
        }
        onChange({ x, y, w, h })
      }
      const onUp = () => {
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
      }
      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
    },
    [rect, onChange, aspect],
  )

  return (
    <div ref={containerRef} style={{ position: 'absolute', inset: 0 }}>
      <div
        onPointerDown={(e) => startDrag('move', e)}
        style={{
          position: 'absolute',
          left: `${rect.x * 100}%`,
          top: `${rect.y * 100}%`,
          width: `${rect.w * 100}%`,
          height: `${rect.h * 100}%`,
          border: '2px solid var(--gold)',
          boxShadow: '0 0 0 2000px rgba(0,0,0,0.5)',
          cursor: 'move',
        }}
      >
        {HANDLES.map((h) => (
          <div
            key={h}
            onPointerDown={(e) => startDrag(h, e)}
            style={{
              position: 'absolute',
              width: 16,
              height: 16,
              background: 'var(--gold)',
              borderRadius: 4,
              top: h.includes('n') ? -8 : undefined,
              bottom: h.includes('s') ? -8 : undefined,
              left: h.includes('w') ? -8 : undefined,
              right: h.includes('e') ? -8 : undefined,
              cursor: `${h}-resize`,
            }}
          />
        ))}
      </div>
    </div>
  )
}
