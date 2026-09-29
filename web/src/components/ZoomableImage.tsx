import { useRef, useState } from 'react'

export interface ZoomableImageProps {
  src: string
  alt: string
  onSwipeLeft?: () => void
  onSwipeRight?: () => void
}

/** Pinch/wheel zoom with pan, double click/tap to zoom, and swipe navigation when not zoomed. */
export function ZoomableImage({ src, alt, onSwipeLeft, onSwipeRight }: ZoomableImageProps): React.ReactElement {
  const [scale, setScale] = useState(1)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const dragging = useRef(false)
  const lastPos = useRef({ x: 0, y: 0 })
  const pinchDist = useRef(0)
  const touchStart = useRef({ x: 0, y: 0, time: 0 })

  const clampScale = (s: number) => Math.min(6, Math.max(1, s))

  const onWheel = (e: React.WheelEvent) => {
    if (!e.ctrlKey) {
      // Plain wheel: treat as zoom too, since the grid uses ctrl+wheel for density.
    }
    e.preventDefault()
    const next = clampScale(scale - e.deltaY * 0.0015 * scale)
    setScale(next)
    if (next === 1) setOffset({ x: 0, y: 0 })
  }

  const onDoubleClick = () => {
    if (scale > 1) {
      setScale(1)
      setOffset({ x: 0, y: 0 })
    } else {
      setScale(2.5)
    }
  }

  const onMouseDown = (e: React.MouseEvent) => {
    if (scale === 1) return
    dragging.current = true
    lastPos.current = { x: e.clientX, y: e.clientY }
  }
  const onMouseMove = (e: React.MouseEvent) => {
    if (!dragging.current) return
    const dx = e.clientX - lastPos.current.x
    const dy = e.clientY - lastPos.current.y
    lastPos.current = { x: e.clientX, y: e.clientY }
    setOffset((o) => ({ x: o.x + dx, y: o.y + dy }))
  }
  const endDrag = () => {
    dragging.current = false
  }

  const dist = (touches: React.TouchList) => {
    const [a, b] = [touches[0], touches[1]]
    return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
  }

  const onTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length === 2) {
      pinchDist.current = dist(e.touches)
    } else if (e.touches.length === 1) {
      touchStart.current = { x: e.touches[0].clientX, y: e.touches[0].clientY, time: Date.now() }
      if (scale > 1) {
        dragging.current = true
        lastPos.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }
      }
    }
  }
  const onTouchMove = (e: React.TouchEvent) => {
    if (e.touches.length === 2) {
      const d = dist(e.touches)
      if (pinchDist.current > 0) {
        const next = clampScale(scale * (d / pinchDist.current))
        setScale(next)
      }
      pinchDist.current = d
    } else if (e.touches.length === 1 && dragging.current) {
      const dx = e.touches[0].clientX - lastPos.current.x
      const dy = e.touches[0].clientY - lastPos.current.y
      lastPos.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }
      setOffset((o) => ({ x: o.x + dx, y: o.y + dy }))
    }
  }
  const onTouchEnd = (e: React.TouchEvent) => {
    dragging.current = false
    pinchDist.current = 0
    if (e.changedTouches.length === 1 && scale === 1) {
      const dx = e.changedTouches[0].clientX - touchStart.current.x
      const dt = Date.now() - touchStart.current.time
      if (dt < 500 && Math.abs(dx) > 60) {
        if (dx < 0) onSwipeLeft?.()
        else onSwipeRight?.()
      }
    }
  }

  return (
    <div
      style={{ width: '100%', height: '100%', overflow: 'hidden', touchAction: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      onWheel={onWheel}
      onDoubleClick={onDoubleClick}
      onMouseDown={onMouseDown}
      onMouseMove={onMouseMove}
      onMouseUp={endDrag}
      onMouseLeave={endDrag}
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
    >
      <img
        src={src}
        alt={alt}
        draggable={false}
        style={{
          maxWidth: '100%',
          maxHeight: '100%',
          transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})`,
          transition: dragging.current ? 'none' : 'transform 0.1s ease-out',
          cursor: scale > 1 ? 'grab' : 'default',
        }}
      />
    </div>
  )
}
