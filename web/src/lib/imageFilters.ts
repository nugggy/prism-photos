// Pure, testable image adjustment maths. All heavy per-pixel work lives here so it can run
// identically on the downscaled live preview canvas and the full resolution export canvas.

export interface Adjustments {
  brightness: number // -100..100
  exposure: number // -100..100
  contrast: number // -100..100
  highlights: number // -100..100
  shadows: number // -100..100
  whites: number // -100..100
  blacks: number // -100..100
  saturation: number // -100..100
  vibrance: number // -100..100
  warmth: number // -100..100
  tint: number // -100..100
  hue: number // -180..180
  sharpness: number // 0..100
  clarity: number // -100..100
  blur: number // 0..100
  vignetteStrength: number // 0..100
  vignetteSoftness: number // 0..100
  grain: number // 0..100
  fade: number // 0..100
  dehaze: number // -100..100
}

export const ADJUSTMENT_KEYS: (keyof Adjustments)[] = [
  'brightness',
  'exposure',
  'contrast',
  'highlights',
  'shadows',
  'whites',
  'blacks',
  'saturation',
  'vibrance',
  'warmth',
  'tint',
  'hue',
  'sharpness',
  'clarity',
  'blur',
  'vignetteStrength',
  'vignetteSoftness',
  'grain',
  'fade',
  'dehaze',
]

export const ADJUSTMENT_RANGES: Record<keyof Adjustments, [number, number]> = {
  brightness: [-100, 100],
  exposure: [-100, 100],
  contrast: [-100, 100],
  highlights: [-100, 100],
  shadows: [-100, 100],
  whites: [-100, 100],
  blacks: [-100, 100],
  saturation: [-100, 100],
  vibrance: [-100, 100],
  warmth: [-100, 100],
  tint: [-100, 100],
  hue: [-180, 180],
  sharpness: [0, 100],
  clarity: [-100, 100],
  blur: [0, 100],
  vignetteStrength: [0, 100],
  vignetteSoftness: [0, 100],
  grain: [0, 100],
  fade: [0, 100],
  dehaze: [-100, 100],
}

export const ADJUSTMENT_LABELS: Record<keyof Adjustments, string> = {
  brightness: 'Brightness',
  exposure: 'Exposure',
  contrast: 'Contrast',
  highlights: 'Highlights',
  shadows: 'Shadows',
  whites: 'Whites',
  blacks: 'Blacks',
  saturation: 'Saturation',
  vibrance: 'Vibrance',
  warmth: 'Warmth',
  tint: 'Tint',
  hue: 'Hue',
  sharpness: 'Sharpness',
  clarity: 'Clarity',
  blur: 'Blur',
  vignetteStrength: 'Vignette',
  vignetteSoftness: 'Vignette softness',
  grain: 'Grain',
  fade: 'Fade',
  dehaze: 'Dehaze',
}

export const DEFAULT_ADJUSTMENTS: Adjustments = {
  brightness: 0,
  exposure: 0,
  contrast: 0,
  highlights: 0,
  shadows: 0,
  whites: 0,
  blacks: 0,
  saturation: 0,
  vibrance: 0,
  warmth: 0,
  tint: 0,
  hue: 0,
  sharpness: 0,
  clarity: 0,
  blur: 0,
  vignetteStrength: 0,
  vignetteSoftness: 50,
  grain: 0,
  fade: 0,
  dehaze: 0,
}

export function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v))
}

export function clampByte(v: number): number {
  return Math.max(0, Math.min(255, v))
}

function clampField(key: keyof Adjustments, value: number): number {
  const [min, max] = ADJUSTMENT_RANGES[key]
  return clamp(value, min, max)
}

/** Clamps every field of an Adjustments object to its valid range. */
export function clampAdjustments(adj: Adjustments): Adjustments {
  const result = { ...adj }
  for (const key of ADJUSTMENT_KEYS) {
    result[key] = clampField(key, adj[key])
  }
  return result
}

export function isDefaultAdjustments(adj: Adjustments): boolean {
  return ADJUSTMENT_KEYS.every((k) => adj[k] === DEFAULT_ADJUSTMENTS[k])
}

// ---------------------------------------------------------------------------
// Filter presets
// ---------------------------------------------------------------------------

export const FILTER_PRESETS = [
  'Original',
  'Vivid',
  'Warm',
  'Cool',
  'Mono',
  'Silver',
  'Sepia',
  'Fade',
  'Noir',
  'Matte',
  'Film',
  'Golden',
  'Teal and Orange',
  'Cinematic',
  'Pastel',
  'High Key',
  'Low Key',
  'Vintage',
] as const

export type FilterPreset = (typeof FILTER_PRESETS)[number]

/** Extra CSS-only look applied alongside the pixel adjustments (kept exact via ctx.filter). */
export interface PresetExtraCss {
  sepia?: number // 0..1
  grayscale?: number // 0..1
}

interface PresetDefinition {
  adjustments: Partial<Adjustments>
  extraCss?: PresetExtraCss
}

const PRESET_DEFINITIONS: Record<FilterPreset, PresetDefinition> = {
  Original: { adjustments: {} },
  Vivid: { adjustments: { brightness: 4, contrast: 14, saturation: 30, vibrance: 18, clarity: 10 } },
  Warm: { adjustments: { brightness: 2, contrast: 4, saturation: 8, warmth: 28 } },
  Cool: { adjustments: { brightness: 2, contrast: 4, saturation: 4, warmth: -28 } },
  Mono: { adjustments: { brightness: 2, contrast: 10, saturation: -100 } },
  Silver: { adjustments: { saturation: -70, contrast: 20, brightness: -2, highlights: -10 }, extraCss: { grayscale: 0.5 } },
  Sepia: { adjustments: { brightness: 4, contrast: 6, saturation: -40, warmth: 45 }, extraCss: { sepia: 0.5 } },
  Fade: { adjustments: { brightness: 8, contrast: -18, saturation: -18, warmth: 6, fade: 30 } },
  Noir: { adjustments: { brightness: -4, contrast: 30, saturation: -100, warmth: -6, vignetteStrength: 20 } },
  Matte: { adjustments: { fade: 30, contrast: -12, blacks: 20, saturation: -10 } },
  Film: { adjustments: { grain: 25, contrast: 8, saturation: -6, warmth: 8, vignetteStrength: 10 } },
  Golden: { adjustments: { warmth: 35, saturation: 12, highlights: 10, brightness: 4 } },
  'Teal and Orange': { adjustments: { shadows: -14, highlights: 14, saturation: 18, contrast: 8 } },
  Cinematic: { adjustments: { contrast: 18, shadows: -10, highlights: 8, saturation: -8, vignetteStrength: 18 } },
  Pastel: { adjustments: { saturation: -20, brightness: 10, contrast: -14, fade: 20 } },
  'High Key': { adjustments: { brightness: 22, whites: 30, shadows: 20, contrast: -10 } },
  'Low Key': { adjustments: { brightness: -20, blacks: -30, shadows: -20, contrast: 20 } },
  Vintage: { adjustments: { warmth: 20, fade: 25, grain: 15, vignetteStrength: 15, saturation: -15 } },
}

export function presetExtraCss(preset: FilterPreset): PresetExtraCss | undefined {
  return PRESET_DEFINITIONS[preset].extraCss
}

/** Returns the full Adjustments for a preset at 100% strength (deltas merged onto the defaults). */
export function presetAdjustments(preset: FilterPreset): Adjustments {
  return clampAdjustments({ ...DEFAULT_ADJUSTMENTS, ...PRESET_DEFINITIONS[preset].adjustments })
}

/**
 * Composes the final Adjustments used for rendering: a filter preset blended in at `strength`
 * percent (0..100), plus the user's manual Adjust-tab deltas layered on top. Both are expressed
 * as deltas from DEFAULT_ADJUSTMENTS so this works even for fields whose default isn't zero.
 */
export function composeAdjustments(preset: FilterPreset, strength: number, manual: Adjustments): Adjustments {
  const presetFull = presetAdjustments(preset)
  const factor = clamp(strength, 0, 100) / 100
  const result = { ...DEFAULT_ADJUSTMENTS }
  for (const key of ADJUSTMENT_KEYS) {
    const base = DEFAULT_ADJUSTMENTS[key]
    const presetDelta = (presetFull[key] - base) * factor
    const manualDelta = manual[key] - base
    result[key] = clampField(key, base + presetDelta + manualDelta)
  }
  return result
}

// ---------------------------------------------------------------------------
// CSS-exact adjustments (applied via ctx.filter)
// ---------------------------------------------------------------------------

/** Builds a CSS canvas filter string for the adjustments that map exactly onto CSS filter functions. */
export function buildCssFilter(adj: Adjustments, extra?: PresetExtraCss): string {
  const brightness = clamp(1 + adj.brightness / 100 + adj.exposure / 130, 0, 4)
  const contrast = clamp(1 + adj.contrast / 100, 0, 4)
  const saturate = clamp(1 + adj.saturation / 100, 0, 4)
  const parts = [`brightness(${brightness})`, `contrast(${contrast})`, `saturate(${saturate})`]
  if (adj.hue !== 0) parts.push(`hue-rotate(${adj.hue}deg)`)
  if (adj.blur > 0) parts.push(`blur(${(adj.blur / 100) * 8}px)`)
  if (extra?.sepia) parts.push(`sepia(${extra.sepia})`)
  if (extra?.grayscale) parts.push(`grayscale(${extra.grayscale})`)
  return parts.join(' ')
}

// ---------------------------------------------------------------------------
// Per-pixel adjustments (not exactly representable by CSS filters)
// ---------------------------------------------------------------------------

/** Tone curve (highlights/shadows/whites/blacks), vibrance, warmth and tint, dehaze and fade. */
export function applyToneAndColour(imageData: ImageData, adj: Adjustments): void {
  const needsWork =
    adj.highlights !== 0 ||
    adj.shadows !== 0 ||
    adj.whites !== 0 ||
    adj.blacks !== 0 ||
    adj.vibrance !== 0 ||
    adj.warmth !== 0 ||
    adj.tint !== 0 ||
    adj.dehaze !== 0 ||
    adj.fade !== 0
  if (!needsWork) return

  const data = imageData.data
  const shadowFactor = adj.shadows / 100
  const highlightFactor = adj.highlights / 100
  const blackFactor = adj.blacks / 100
  const whiteFactor = adj.whites / 100
  const dehazeFactor = adj.dehaze / 100
  const fadeFactor = adj.fade / 100
  const warmthShift = Math.round((adj.warmth / 100) * 40)
  const tintShift = Math.round((adj.tint / 100) * 40)

  for (let i = 0; i < data.length; i += 4) {
    let r = data[i]
    let g = data[i + 1]
    let b = data[i + 2]

    const luma = 0.299 * r + 0.587 * g + 0.114 * b
    const lumaN = luma / 255

    if (shadowFactor !== 0 || highlightFactor !== 0 || blackFactor !== 0 || whiteFactor !== 0) {
      const shadowWeight = Math.pow(1 - lumaN, 2)
      const highlightWeight = Math.pow(lumaN, 2)
      const blackWeight = Math.pow(1 - lumaN, 3)
      const whiteWeight = Math.pow(lumaN, 3)
      const delta = shadowFactor * 55 * shadowWeight + highlightFactor * 55 * highlightWeight + blackFactor * 50 * blackWeight + whiteFactor * 50 * whiteWeight
      r += delta
      g += delta
      b += delta
    }

    if (adj.vibrance !== 0) {
      const max = Math.max(r, g, b)
      const avg = (r + g + b) / 3
      const currentSat = (max - avg) / 128
      const amt = (adj.vibrance / 100) * (1 - clamp(currentSat, 0, 1)) * 60
      r += (r - avg) * (amt / 128)
      g += (g - avg) * (amt / 128)
      b += (b - avg) * (amt / 128)
    }

    if (warmthShift !== 0) {
      r += warmthShift
      b -= warmthShift
    }
    if (tintShift !== 0) {
      g += tintShift
      r -= tintShift / 2
      b -= tintShift / 2
    }

    if (dehazeFactor !== 0) {
      const k = 1 + dehazeFactor * 0.5
      r = (r - 128) * k + 128 - dehazeFactor * 20
      g = (g - 128) * k + 128 - dehazeFactor * 20
      b = (b - 128) * k + 128 - dehazeFactor * 20
    }

    if (fadeFactor > 0) {
      const lift = fadeFactor * 40
      const k = 1 - fadeFactor * 0.35
      r = (r - 128) * k + 128 + lift * 0.5
      g = (g - 128) * k + 128 + lift * 0.5
      b = (b - 128) * k + 128 + lift * 0.5
      const favg = (r + g + b) / 3
      r += (favg - r) * fadeFactor * 0.25
      g += (favg - g) * fadeFactor * 0.25
      b += (favg - b) * fadeFactor * 0.25
    }

    data[i] = clampByte(r)
    data[i + 1] = clampByte(g)
    data[i + 2] = clampByte(b)
  }
}

/** Simple box-blur based unsharp mask used for both sharpness (fine detail) and clarity (local contrast). */
export function applySharpnessAndClarity(imageData: ImageData, sharpness: number, clarity: number): void {
  if (sharpness <= 0 && clarity === 0) return
  const { width, height, data } = imageData
  const src = new Uint8ClampedArray(data)

  if (sharpness > 0) {
    const amt = sharpness / 100
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const i = (y * width + x) * 4
        for (let c = 0; c < 3; c++) {
          const centre = src[i + c]
          const up = src[i + c - (y > 0 ? width * 4 : 0)]
          const down = src[i + c + (y < height - 1 ? width * 4 : 0)]
          const left = src[i + c - (x > 0 ? 4 : 0)]
          const right = src[i + c + (x < width - 1 ? 4 : 0)]
          const blur = (up + down + left + right + centre) / 5
          const sharpened = centre + (centre - blur) * amt * 2.2
          data[i + c] = clampByte(sharpened)
        }
      }
    }
  }

  if (clarity !== 0) {
    const amt = clarity / 100
    const radius = Math.max(2, Math.round(Math.min(width, height) / 100))
    const blurred = boxBlurLuma(sharpness > 0 ? data : src, width, height, radius)
    for (let i = 0; i < data.length; i += 4) {
      const idx = i / 4
      const localAmt = amt * 0.9
      data[i] = clampByte(data[i] + (data[i] - blurred[idx]) * localAmt)
      data[i + 1] = clampByte(data[i + 1] + (data[i + 1] - blurred[idx]) * localAmt)
      data[i + 2] = clampByte(data[i + 2] + (data[i + 2] - blurred[idx]) * localAmt)
    }
  }
}

/** Returns a per-pixel luma box blur (used as the "low frequency" layer for clarity). */
function boxBlurLuma(data: Uint8ClampedArray, width: number, height: number, radius: number): Float32Array {
  const luma = new Float32Array(width * height)
  for (let i = 0, p = 0; i < data.length; i += 4, p++) {
    luma[p] = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2]
  }
  const horiz = new Float32Array(width * height)
  for (let y = 0; y < height; y++) {
    let sum = 0
    const rowStart = y * width
    for (let x = -radius; x <= radius; x++) {
      sum += luma[rowStart + clamp(x, 0, width - 1)]
    }
    for (let x = 0; x < width; x++) {
      horiz[rowStart + x] = sum / (radius * 2 + 1)
      const addX = clamp(x + radius + 1, 0, width - 1)
      const subX = clamp(x - radius, 0, width - 1)
      sum += luma[rowStart + addX] - luma[rowStart + subX]
    }
  }
  const out = new Float32Array(width * height)
  for (let x = 0; x < width; x++) {
    let sum = 0
    for (let y = -radius; y <= radius; y++) {
      sum += horiz[clamp(y, 0, height - 1) * width + x]
    }
    for (let y = 0; y < height; y++) {
      out[y * width + x] = sum / (radius * 2 + 1)
      const addY = clamp(y + radius + 1, 0, height - 1)
      const subY = clamp(y - radius, 0, height - 1)
      sum += horiz[addY * width + x] - horiz[subY * width + x]
    }
  }
  return out
}

/** Radial darkening from the edges inward. Softness controls how gradual the falloff is. */
export function applyVignette(imageData: ImageData, strength: number, softness: number): void {
  if (strength <= 0) return
  const { width, height, data } = imageData
  const cx = width / 2
  const cy = height / 2
  const maxDist = Math.sqrt(cx * cx + cy * cy)
  const soft = clamp(softness, 0, 100) / 100
  const innerRadius = maxDist * (1 - soft) * 0.6
  const amt = strength / 100

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const dx = x - cx
      const dy = y - cy
      const dist = Math.sqrt(dx * dx + dy * dy)
      const t = clamp((dist - innerRadius) / Math.max(1, maxDist - innerRadius), 0, 1)
      const darken = 1 - t * t * amt * 0.85
      const i = (y * width + x) * 4
      data[i] *= darken
      data[i + 1] *= darken
      data[i + 2] *= darken
    }
  }
}

/** Deterministic-when-seeded pseudo random number generator (mulberry32). */
function mulberry32(seed: number): () => number {
  let a = seed
  return () => {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Adds luminance noise. Pass a fixed seed for deterministic output (tests, thumbnails). */
export function applyGrain(imageData: ImageData, amount: number, seed = Date.now()): void {
  if (amount <= 0) return
  const data = imageData.data
  const rand = mulberry32(seed)
  const strength = (amount / 100) * 45
  for (let i = 0; i < data.length; i += 4) {
    const noise = (rand() - 0.5) * strength
    data[i] = clampByte(data[i] + noise)
    data[i + 1] = clampByte(data[i + 1] + noise)
    data[i + 2] = clampByte(data[i + 2] + noise)
  }
}

/** Runs the full non-CSS pixel pipeline in a sensible order. Mutates imageData in place. */
export function applyPixelPipeline(imageData: ImageData, adj: Adjustments, seed?: number): void {
  applyToneAndColour(imageData, adj)
  applySharpnessAndClarity(imageData, adj.sharpness, adj.clarity)
  applyVignette(imageData, adj.vignetteStrength, adj.vignetteSoftness)
  applyGrain(imageData, adj.grain, seed)
}

/** Legacy helper kept for the existing warmth-only call sites / tests. */
export function applyWarmth(imageData: ImageData, warmth: number): void {
  if (warmth === 0) return
  const shift = Math.round((warmth / 100) * 40)
  const data = imageData.data
  for (let i = 0; i < data.length; i += 4) {
    data[i] = clampByte(data[i] + shift)
    data[i + 2] = clampByte(data[i + 2] - shift)
  }
}

// ---------------------------------------------------------------------------
// Auto enhance
// ---------------------------------------------------------------------------

/**
 * Analyses the image histogram and returns Adjust-tab deltas that stretch contrast to use the
 * full tonal range, plus a mild saturation and clarity lift.
 */
export function computeAutoEnhanceAdjustments(imageData: ImageData): Partial<Adjustments> {
  const data = imageData.data
  const histogram = new Array<number>(256).fill(0)
  let total = 0
  for (let i = 0; i < data.length; i += 4) {
    const luma = Math.round(0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2])
    histogram[luma] += 1
    total += 1
  }
  if (total === 0) return {}

  const lowClip = total * 0.006
  const highClip = total * 0.994
  let cumulative = 0
  let low = 0
  let high = 255
  for (let v = 0; v < 256; v++) {
    cumulative += histogram[v]
    if (cumulative >= lowClip) {
      low = v
      break
    }
  }
  cumulative = 0
  for (let v = 255; v >= 0; v--) {
    cumulative += histogram[v]
    if (cumulative >= total - highClip) {
      high = v
      break
    }
  }
  if (high <= low) return { saturation: 8, vibrance: 10, clarity: 8 }

  const range = high - low
  const blacks = clamp(((low - 0) / 255) * -140, -100, 0)
  const whites = clamp(((255 - high) / 255) * 140, 0, 100)
  const contrast = clamp(((255 - range) / 255) * 35, 0, 40)

  return {
    blacks,
    whites,
    contrast,
    saturation: 8,
    vibrance: 12,
    clarity: 8,
  }
}
