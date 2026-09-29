import { describe, expect, it } from 'vitest'
import {
  clampCropToBounds,
  computeStraightenCropRect,
  computeStraightenSafeRect,
  cropRectForRatio,
  rotatedBoundingBox,
  rotatedRectMaxArea,
} from './crop'

describe('rotatedRectMaxArea', () => {
  it('returns the original size at 0 degrees', () => {
    const { w, h } = rotatedRectMaxArea(400, 300, 0)
    expect(w).toBe(400)
    expect(h).toBe(300)
  })

  it('shrinks as the angle increases away from 0', () => {
    const small = rotatedRectMaxArea(400, 300, 5)
    const large = rotatedRectMaxArea(400, 300, 15)
    expect(small.w * small.h).toBeGreaterThan(large.w * large.h)
    expect(small.w).toBeLessThanOrEqual(400)
    expect(small.h).toBeLessThanOrEqual(300)
  })

  it('handles a square image', () => {
    const { w, h } = rotatedRectMaxArea(500, 500, 45)
    expect(w).toBeGreaterThan(0)
    expect(h).toBeGreaterThan(0)
    expect(w).toBeLessThanOrEqual(500)
  })
})

describe('computeStraightenCropRect', () => {
  it('is the full rect at 0 degrees', () => {
    expect(computeStraightenCropRect(1000, 800, 0)).toEqual({ x: 0, y: 0, w: 1, h: 1 })
  })

  it('is centred and shrinks as the angle grows', () => {
    const rect = computeStraightenCropRect(1000, 800, 10)
    expect(rect.x).toBeCloseTo((1 - rect.w) / 2, 5)
    expect(rect.y).toBeCloseTo((1 - rect.h) / 2, 5)
    expect(rect.w).toBeLessThan(1)
    expect(rect.h).toBeLessThan(1)
  })
})

describe('rotatedBoundingBox', () => {
  it('is unchanged at 0 degrees', () => {
    expect(rotatedBoundingBox(400, 300, 0)).toEqual({ w: 400, h: 300 })
  })

  it('grows as the angle increases towards 45 degrees', () => {
    const box10 = rotatedBoundingBox(400, 300, 10)
    const box45 = rotatedBoundingBox(400, 300, 45)
    expect(box45.w).toBeGreaterThan(box10.w)
    expect(box45.h).toBeGreaterThan(box10.h)
  })
})

describe('computeStraightenSafeRect', () => {
  it('is the full rect at 0 degrees', () => {
    expect(computeStraightenSafeRect(1000, 800, 0)).toEqual({ x: 0, y: 0, w: 1, h: 1 })
  })

  it('is centred and shrinks as the angle grows, staying within the grown canvas', () => {
    const rect = computeStraightenSafeRect(1000, 800, 12)
    expect(rect.x).toBeCloseTo((1 - rect.w) / 2, 5)
    expect(rect.w).toBeGreaterThan(0)
    expect(rect.w).toBeLessThan(1)
    expect(rect.h).toBeLessThan(1)
  })
})

describe('clampCropToBounds', () => {
  it('keeps a rect that already fits unchanged in size', () => {
    const result = clampCropToBounds({ x: 0.1, y: 0.1, w: 0.5, h: 0.5 }, { x: 0, y: 0, w: 1, h: 1 })
    expect(result.w).toBe(0.5)
    expect(result.h).toBe(0.5)
  })

  it('shrinks a rect larger than its bounds', () => {
    const result = clampCropToBounds({ x: 0, y: 0, w: 1, h: 1 }, { x: 0.2, y: 0.2, w: 0.4, h: 0.4 })
    expect(result.w).toBe(0.4)
    expect(result.h).toBe(0.4)
  })
})

describe('cropRectForRatio', () => {
  it('returns the current rect unchanged for free ratio', () => {
    const rect = { x: 0.1, y: 0.1, w: 0.4, h: 0.4 }
    expect(cropRectForRatio(rect, null, 1000, 1000)) .toEqual(rect)
  })

  it('produces a rect within bounds for a square canvas and 16:9 ratio', () => {
    const rect = cropRectForRatio({ x: 0.25, y: 0.25, w: 0.5, h: 0.5 }, 16 / 9, 1000, 1000)
    expect(rect.x).toBeGreaterThanOrEqual(0)
    expect(rect.y).toBeGreaterThanOrEqual(0)
    expect(rect.x + rect.w).toBeLessThanOrEqual(1.0001)
    expect(rect.y + rect.h).toBeLessThanOrEqual(1.0001)
  })
})
