import { describe, expect, it } from 'vitest'
import {
  ADJUSTMENT_KEYS,
  DEFAULT_ADJUSTMENTS,
  FILTER_PRESETS,
  applyGrain,
  applyToneAndColour,
  applyVignette,
  applyWarmth,
  buildCssFilter,
  clampAdjustments,
  clampByte,
  composeAdjustments,
  computeAutoEnhanceAdjustments,
  isDefaultAdjustments,
  presetAdjustments,
  type Adjustments,
} from './imageFilters'

function makeImageData(width: number, height: number, fill = 128): ImageData {
  const data = new Uint8ClampedArray(width * height * 4)
  for (let i = 0; i < data.length; i += 4) {
    data[i] = fill
    data[i + 1] = fill
    data[i + 2] = fill
    data[i + 3] = 255
  }
  return { data, width, height, colorSpace: 'srgb' } as ImageData
}

describe('clampByte', () => {
  it('clamps to 0..255', () => {
    expect(clampByte(-10)).toBe(0)
    expect(clampByte(300)).toBe(255)
    expect(clampByte(128)).toBe(128)
  })
})

describe('clampAdjustments', () => {
  it('clamps every field to its valid range', () => {
    const adj: Adjustments = { ...DEFAULT_ADJUSTMENTS, brightness: 500, hue: -999, sharpness: -10, blacks: -500 }
    const clamped = clampAdjustments(adj)
    expect(clamped.brightness).toBe(100)
    expect(clamped.hue).toBe(-180)
    expect(clamped.sharpness).toBe(0)
    expect(clamped.blacks).toBe(-100)
  })
})

describe('isDefaultAdjustments', () => {
  it('is true for the default object and false after any change', () => {
    expect(isDefaultAdjustments(DEFAULT_ADJUSTMENTS)).toBe(true)
    expect(isDefaultAdjustments({ ...DEFAULT_ADJUSTMENTS, contrast: 1 })).toBe(false)
  })
})

describe('buildCssFilter', () => {
  it('is the identity filter for default adjustments', () => {
    expect(buildCssFilter(DEFAULT_ADJUSTMENTS)).toBe('brightness(1) contrast(1) saturate(1)')
  })

  it('includes hue-rotate and blur only when non-zero', () => {
    const withHue = buildCssFilter({ ...DEFAULT_ADJUSTMENTS, hue: 30 })
    expect(withHue).toContain('hue-rotate(30deg)')
    const withBlur = buildCssFilter({ ...DEFAULT_ADJUSTMENTS, blur: 50 })
    expect(withBlur).toContain('blur(4px)')
  })

  it('appends preset extra css when provided', () => {
    const withExtra = buildCssFilter(DEFAULT_ADJUSTMENTS, { sepia: 0.5, grayscale: 0.2 })
    expect(withExtra).toContain('sepia(0.5)')
    expect(withExtra).toContain('grayscale(0.2)')
  })
})

describe('FILTER_PRESETS / presetAdjustments', () => {
  it('has at least 16 presets including Original', () => {
    expect(FILTER_PRESETS.length).toBeGreaterThanOrEqual(16)
    expect(FILTER_PRESETS).toContain('Original')
  })

  it('Original preset equals the defaults', () => {
    expect(presetAdjustments('Original')).toEqual(DEFAULT_ADJUSTMENTS)
  })

  it('every preset produces adjustments within valid ranges', () => {
    for (const preset of FILTER_PRESETS) {
      const adj = presetAdjustments(preset)
      const clamped = clampAdjustments(adj)
      expect(adj).toEqual(clamped)
    }
  })
})

describe('composeAdjustments', () => {
  it('returns the defaults for Original at 0 manual adjustment', () => {
    expect(composeAdjustments('Original', 100, DEFAULT_ADJUSTMENTS)).toEqual(DEFAULT_ADJUSTMENTS)
  })

  it('scales the preset by strength', () => {
    const full = composeAdjustments('Vivid', 100, DEFAULT_ADJUSTMENTS)
    const half = composeAdjustments('Vivid', 50, DEFAULT_ADJUSTMENTS)
    expect(half.contrast).toBeCloseTo(full.contrast / 2, 5)
    expect(half.saturation).toBeCloseTo(full.saturation / 2, 5)
  })

  it('layers manual adjustments on top of the preset', () => {
    const result = composeAdjustments('Original', 0, { ...DEFAULT_ADJUSTMENTS, brightness: 20 })
    expect(result.brightness).toBe(20)
  })

  it('clamps the combined result', () => {
    const result = composeAdjustments('Vivid', 100, { ...DEFAULT_ADJUSTMENTS, saturation: 100 })
    expect(result.saturation).toBe(100)
  })
})

describe('applyToneAndColour', () => {
  it('is a no-op for default adjustments', () => {
    const img = makeImageData(2, 2, 100)
    const before = new Uint8ClampedArray(img.data)
    applyToneAndColour(img, DEFAULT_ADJUSTMENTS)
    expect(img.data).toEqual(before)
  })

  it('warmth shifts red up and blue down', () => {
    const img = makeImageData(1, 1, 128)
    applyToneAndColour(img, { ...DEFAULT_ADJUSTMENTS, warmth: 100 })
    expect(img.data[0]).toBeGreaterThan(128)
    expect(img.data[2]).toBeLessThan(128)
  })

  it('cool warmth shifts blue up and red down', () => {
    const img = makeImageData(1, 1, 128)
    applyToneAndColour(img, { ...DEFAULT_ADJUSTMENTS, warmth: -100 })
    expect(img.data[0]).toBeLessThan(128)
    expect(img.data[2]).toBeGreaterThan(128)
  })
})

describe('applyWarmth (legacy helper)', () => {
  it('does nothing at zero', () => {
    const img = makeImageData(1, 1, 100)
    const before = new Uint8ClampedArray(img.data)
    applyWarmth(img, 0)
    expect(img.data).toEqual(before)
  })
})

describe('applyVignette', () => {
  it('does nothing at zero strength', () => {
    const img = makeImageData(10, 10, 200)
    const before = new Uint8ClampedArray(img.data)
    applyVignette(img, 0, 50)
    expect(img.data).toEqual(before)
  })

  it('darkens corners more than the centre', () => {
    const img = makeImageData(20, 20, 200)
    applyVignette(img, 100, 30)
    const centreIdx = (10 * 20 + 10) * 4
    const cornerIdx = 0
    expect(img.data[cornerIdx]).toBeLessThan(img.data[centreIdx])
  })
})

describe('applyGrain', () => {
  it('does nothing at zero amount', () => {
    const img = makeImageData(4, 4, 128)
    const before = new Uint8ClampedArray(img.data)
    applyGrain(img, 0, 1)
    expect(img.data).toEqual(before)
  })

  it('is deterministic for a fixed seed and changes pixel values', () => {
    const imgA = makeImageData(4, 4, 128)
    const imgB = makeImageData(4, 4, 128)
    applyGrain(imgA, 40, 42)
    applyGrain(imgB, 40, 42)
    expect(imgA.data).toEqual(imgB.data)
    expect(imgA.data).not.toEqual(makeImageData(4, 4, 128).data)
  })
})

describe('computeAutoEnhanceAdjustments', () => {
  it('suggests a mild boost for a flat mid-grey image', () => {
    const img = makeImageData(8, 8, 128)
    const result = computeAutoEnhanceAdjustments(img)
    expect(result.saturation).toBeGreaterThan(0)
    expect(result.clarity).toBeGreaterThan(0)
  })

  it('returns an object with only valid adjustment keys', () => {
    const img = makeImageData(8, 8, 90)
    const result = computeAutoEnhanceAdjustments(img)
    for (const key of Object.keys(result)) {
      expect(ADJUSTMENT_KEYS).toContain(key)
    }
  })
})
