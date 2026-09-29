export interface Adjustments {
  brightness: number // -100..100
  contrast: number // -100..100
  saturation: number // -100..100
  warmth: number // -100..100
}

export const DEFAULT_ADJUSTMENTS: Adjustments = { brightness: 0, contrast: 0, saturation: 0, warmth: 0 }

export type FilterPreset = 'Original' | 'Vivid' | 'Warm' | 'Cool' | 'Mono' | 'Sepia' | 'Fade' | 'Noir'

export const FILTER_PRESETS: FilterPreset[] = ['Original', 'Vivid', 'Warm', 'Cool', 'Mono', 'Sepia', 'Fade', 'Noir']

export function presetAdjustments(preset: FilterPreset): Adjustments {
  switch (preset) {
    case 'Vivid':
      return { brightness: 4, contrast: 14, saturation: 30, warmth: 4 }
    case 'Warm':
      return { brightness: 2, contrast: 4, saturation: 8, warmth: 28 }
    case 'Cool':
      return { brightness: 2, contrast: 4, saturation: 4, warmth: -28 }
    case 'Mono':
      return { brightness: 2, contrast: 10, saturation: -100, warmth: 0 }
    case 'Sepia':
      return { brightness: 4, contrast: 6, saturation: -40, warmth: 45 }
    case 'Fade':
      return { brightness: 8, contrast: -18, saturation: -18, warmth: 6 }
    case 'Noir':
      return { brightness: -4, contrast: 30, saturation: -100, warmth: -6 }
    case 'Original':
    default:
      return DEFAULT_ADJUSTMENTS
  }
}

/** Builds a CSS canvas filter string for brightness/contrast/saturation (warmth is applied per-pixel). */
export function buildCssFilter(adj: Adjustments): string {
  const brightness = 1 + adj.brightness / 100
  const contrast = 1 + adj.contrast / 100
  const saturate = 1 + adj.saturation / 100
  return `brightness(${brightness}) contrast(${contrast}) saturate(${saturate})`
}

/** Applies warmth by shifting red/blue channels directly on pixel data. */
export function applyWarmth(imageData: ImageData, warmth: number): void {
  if (warmth === 0) return
  const shift = Math.round((warmth / 100) * 40)
  const data = imageData.data
  for (let i = 0; i < data.length; i += 4) {
    data[i] = clampByte(data[i] + shift)
    data[i + 2] = clampByte(data[i + 2] - shift)
  }
}

function clampByte(v: number): number {
  return Math.max(0, Math.min(255, v))
}
