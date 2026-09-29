// Crop ratio presets and the straighten auto-crop maths.

export interface CropRect {
  x: number // 0..1
  y: number // 0..1
  w: number // 0..1
  h: number // 0..1
}

export interface CropRatio {
  label: string
  value: number | null // width / height, or null for free / original
}

export const CROP_RATIOS: CropRatio[] = [
  { label: 'Free', value: null },
  { label: '1:1', value: 1 },
  { label: '4:3', value: 4 / 3 },
  { label: '3:2', value: 3 / 2 },
  { label: '16:9', value: 16 / 9 },
  { label: '9:16', value: 9 / 16 },
  { label: '5:4', value: 5 / 4 },
]

export const FULL_CROP: CropRect = { x: 0, y: 0, w: 1, h: 1 }

/**
 * Largest axis-aligned rectangle (with the same centre) that fits entirely within a `w`x`h`
 * rectangle after it has been rotated by `angleDeg` degrees, leaving no empty corners.
 * Standard "rotate and crop" formula.
 */
export function rotatedRectMaxArea(w: number, h: number, angleDeg: number): { w: number; h: number } {
  if (w <= 0 || h <= 0) return { w: 0, h: 0 }
  const angle = (((angleDeg % 180) + 180) % 180) * (Math.PI / 180)
  const normalisedAngle = angle > Math.PI / 2 ? Math.PI - angle : angle
  if (normalisedAngle < 1e-6) return { w, h }

  const widthIsLonger = w >= h
  const sideLong = widthIsLonger ? w : h
  const sideShort = widthIsLonger ? h : w
  const sinA = Math.sin(normalisedAngle)
  const cosA = Math.cos(normalisedAngle)

  let wr: number
  let hr: number
  if (sideShort <= 2 * sinA * cosA * sideLong || Math.abs(sinA - cosA) < 1e-10) {
    const x = 0.5 * sideShort
    if (widthIsLonger) {
      wr = x / sinA
      hr = x / cosA
    } else {
      wr = x / cosA
      hr = x / sinA
    }
  } else {
    const cos2a = cosA * cosA - sinA * sinA
    wr = (w * cosA - h * sinA) / cos2a
    hr = (h * cosA - w * sinA) / cos2a
  }
  return { w: Math.max(1, Math.min(w, Math.abs(wr))), h: Math.max(1, Math.min(h, Math.abs(hr))) }
}

/** Bounding box (in the same units as w/h) of a w x h rectangle rotated by angleDeg about its centre. */
export function rotatedBoundingBox(w: number, h: number, angleDeg: number): { w: number; h: number } {
  const rad = (Math.abs(angleDeg) * Math.PI) / 180
  const sin = Math.abs(Math.sin(rad))
  const cos = Math.abs(Math.cos(rad))
  return { w: w * cos + h * sin, h: w * sin + h * cos }
}

/** Centred, normalised crop rect that removes the empty corners left by straightening. */
export function computeStraightenCropRect(imgW: number, imgH: number, angleDeg: number): CropRect {
  if (imgW <= 0 || imgH <= 0 || angleDeg === 0) return { ...FULL_CROP }
  const { w, h } = rotatedRectMaxArea(imgW, imgH, angleDeg)
  const normW = clamp01(w / imgW)
  const normH = clamp01(h / imgH)
  return { x: (1 - normW) / 2, y: (1 - normH) / 2, w: normW, h: normH }
}

/**
 * Same as computeStraightenCropRect, but normalised against the *grown* bounding-box canvas that
 * the renderer actually draws the straightened image onto (i.e. what the crop tool operates on).
 */
export function computeStraightenSafeRect(baseW: number, baseH: number, angleDeg: number): CropRect {
  if (baseW <= 0 || baseH <= 0 || angleDeg === 0) return { ...FULL_CROP }
  const safe = rotatedRectMaxArea(baseW, baseH, angleDeg)
  const grown = rotatedBoundingBox(baseW, baseH, angleDeg)
  const w = clamp01(safe.w / grown.w)
  const h = clamp01(safe.h / grown.h)
  return { x: (1 - w) / 2, y: (1 - h) / 2, w, h }
}

/** Clamps a crop rect defined by its ratio to fit within the given bounds, keeping its centre. */
export function clampCropToBounds(rect: CropRect, bounds: CropRect): CropRect {
  const w = Math.min(rect.w, bounds.w)
  const h = Math.min(rect.h, bounds.h)
  const x = clamp(rect.x, bounds.x, bounds.x + bounds.w - w)
  const y = clamp(rect.y, bounds.y, bounds.y + bounds.h - h)
  return { x, y, w, h }
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v))
}

function clamp01(v: number): number {
  return clamp(v, 0, 1)
}

/** Returns a crop rect matching the given ratio, centred within the current rect's bounding box. */
export function cropRectForRatio(current: CropRect, ratio: number | null, canvasW: number, canvasH: number): CropRect {
  if (ratio === null) return current
  const cx = current.x + current.w / 2
  const cy = current.y + current.h / 2
  let w = current.w
  let h = (w * canvasW) / ratio / canvasH
  if (h > 1) {
    h = 1
    w = (h * canvasH * ratio) / canvasW
  }
  let x = cx - w / 2
  let y = cy - h / 2
  x = clamp01(x)
  y = clamp01(y)
  if (x + w > 1) x = 1 - w
  if (y + h > 1) y = 1 - h
  return { x, y, w, h }
}
