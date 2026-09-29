// Named adjustment presets (saved locally) plus a simple copy/paste clipboard for edits.

import { DEFAULT_ADJUSTMENTS, type Adjustments, type FilterPreset } from './imageFilters'
import { DEFAULT_FRAME_SETTINGS, type FrameSettings } from './frames'

const PRESETS_KEY = 'plexgallery.editor.presets'
const CLIPBOARD_KEY = 'plexgallery.editor.clipboard'
export const MAX_PRESETS = 20

export interface EditorPreset {
  id: string
  name: string
  adjustments: Adjustments
  filterPreset: FilterPreset
  filterStrength: number
  createdAt: number
}

export interface EditClipboard {
  adjustments: Adjustments
  filterPreset: FilterPreset
  filterStrength: number
  frame: FrameSettings
}

function readJson<T>(key: string): T | null {
  try {
    const raw = window.localStorage.getItem(key)
    if (!raw) return null
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

function writeJson(key: string, value: unknown): void {
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Storage unavailable or full; silently ignore so the editor keeps working.
  }
}

export function loadPresets(): EditorPreset[] {
  return readJson<EditorPreset[]>(PRESETS_KEY) ?? []
}

/** Saves a new named preset, trimming the oldest entries beyond MAX_PRESETS. */
export function savePreset(name: string, adjustments: Adjustments, filterPreset: FilterPreset, filterStrength: number): EditorPreset[] {
  const existing = loadPresets()
  const preset: EditorPreset = {
    id: `p_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`,
    name: name.trim() || 'Untitled preset',
    adjustments,
    filterPreset,
    filterStrength,
    createdAt: Date.now(),
  }
  const next = [...existing, preset]
  while (next.length > MAX_PRESETS) next.shift()
  writeJson(PRESETS_KEY, next)
  return next
}

export function deletePreset(id: string): EditorPreset[] {
  const next = loadPresets().filter((p) => p.id !== id)
  writeJson(PRESETS_KEY, next)
  return next
}

export function applyPresetTo(preset: EditorPreset): { adjustments: Adjustments; filterPreset: FilterPreset; filterStrength: number } {
  return { adjustments: preset.adjustments, filterPreset: preset.filterPreset, filterStrength: preset.filterStrength }
}

export function copyEditsToClipboard(clip: EditClipboard): void {
  writeJson(CLIPBOARD_KEY, clip)
}

export function pasteEditsFromClipboard(): EditClipboard | null {
  const clip = readJson<EditClipboard>(CLIPBOARD_KEY)
  if (!clip) return null
  return {
    adjustments: { ...DEFAULT_ADJUSTMENTS, ...clip.adjustments },
    filterPreset: clip.filterPreset,
    filterStrength: clip.filterStrength,
    frame: { ...DEFAULT_FRAME_SETTINGS, ...clip.frame },
  }
}

export function hasClipboardEdits(): boolean {
  return readJson<EditClipboard>(CLIPBOARD_KEY) !== null
}
