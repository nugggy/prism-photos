import { useCallback, useRef, useState } from 'react'

export interface CropRect {
  x: number // 0..1
  y: number // 0..1
  w: number // 0..1
  h: number // 0..1
}

export interface CropOverlayProps {
  rect: CropRect
  onChange: (rect: CropRect) => void
  onCommit?: (rect: CropRect) => void
  aspect: number | null // width/height, or null for free
  /** Region (normalised to the container) the crop rect must stay inside of, e.g. the straighten safe area. */
  bounds?: CropRect
}

const HANDLES = ['nw', 'ne', 'sw', 'se'] as const

export function CropOverlay({ rect, onChange, onCommit, aspect, bounds }: CropOverlayProps): React.ReactElement {
  const containerRef = useRef<HTMLDivElement>(null)
  const [dragging, setDragging] = useState(false)
  const safe = bounds ?? { x: 0, y: 0, w: 1, h: 1 }

  const clampToSafe = useCallback(
    (v: number, min: number, max: number) => Math.max(min, Math.min(max, v)),
    [],
  )

  const startDrag = useCallback(
    (handle: (typeof HANDLES)[number] | 'move', e: React.PointerEvent) => {
      e.stopPropagation()
      e.preventDefault()
      const container = containerRef.current
      if (!container) return
      const bnds = container.getBoundingClientRect()
      const startRect = { ...rect }
      const startX = e.clientX
      const startY = e.clientY
      let latestRect = startRect
      setDragging(true)

      const onMove = (ev: PointerEvent) => {
        const dx = (ev.clientX - startX) / bnds.width
        const dy = (ev.clientY - startY) / bnds.height
        let { x, y, w, h } = startRect

        if (handle === 'move') {
          x = clampToSafe(startRect.x + dx, safe.x, safe.x + safe.w - w)
          y = clampToSafe(startRect.y + dy, safe.y, safe.y + safe.h - h)
        } else {
          let left = startRect.x
          let top = startRect.y
          let right = startRect.x + startRect.w
          let bottom = startRect.y + startRect.h
          if (handle.includes('w')) left = clampToSafe(startRect.x + dx, safe.x, right - 0.05)
          if (handle.includes('e')) right = clampToSafe(startRect.x + startRect.w + dx, left + 0.05, safe.x + safe.w)
          if (handle.includes('n')) top = clampToSafe(startRect.y + dy, safe.y, bottom - 0.05)
          if (handle.includes('s')) bottom = clampToSafe(startRect.y + startRect.h + dy, top + 0.05, safe.y + safe.h)

          w = Math.max(0.05, right - left)
          h = Math.max(0.05, bottom - top)
          if (aspect) {
            // Maintain aspect ratio using width as the driver.
            h = w / aspect / (bnds.width / bnds.height)
            if (top + h > safe.y + safe.h) h = safe.y + safe.h - top
            w = h * aspect * (bnds.width / bnds.height)
          }
          x = left
          y = top
        }
        latestRect = { x, y, w, h }
        onChange(latestRect)
      }
      const onUp = () => {
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
        setDragging(false)
        onCommit?.(latestRect)
      }
      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
    },
    [rect, onChange, onCommit, aspect, safe, clampToSafe],
  )

  return (
    <div ref={containerRef} style={{ position: 'absolute', inset: 0 }}>
      {safe.x > 0.001 || safe.y > 0.001 || safe.w < 0.999 || safe.h < 0.999 ? (
        <div
          style={{
            position: 'absolute',
            left: `${safe.x * 100}%`,
            top: `${safe.y * 100}%`,
            width: `${safe.w * 100}%`,
            height: `${safe.h * 100}%`,
            outline: '1px dashed rgba(255,255,255,0.35)',
            pointerEvents: 'none',
          }}
        />
      ) : null}
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
          touchAction: 'none',
        }}
      >
        {dragging && (
          <div aria-hidden="true" style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
            {[1, 2].map((i) => (
              <div key={`v${i}`} style={{ position: 'absolute', left: `${(i / 3) * 100}%`, top: 0, bottom: 0, width: 1, background: 'rgba(255,255,255,0.6)' }} />
            ))}
            {[1, 2].map((i) => (
              <div key={`h${i}`} style={{ position: 'absolute', top: `${(i / 3) * 100}%`, left: 0, right: 0, height: 1, background: 'rgba(255,255,255,0.6)' }} />
            ))}
          </div>
        )}
        {HANDLES.map((h) => (
          <div
            key={h}
            onPointerDown={(e) => startDrag(h, e)}
            role="button"
            tabIndex={0}
            aria-label={`Resize crop from the ${h === 'nw' ? 'top left' : h === 'ne' ? 'top right' : h === 'sw' ? 'bottom left' : 'bottom right'}`}
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
              touchAction: 'none',
            }}
          />
        ))}
      </div>
    </div>
  )
}
