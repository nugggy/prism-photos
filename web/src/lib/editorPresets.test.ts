import { beforeEach, describe, expect, it } from 'vitest'
import { DEFAULT_ADJUSTMENTS } from './imageFilters'
import { DEFAULT_FRAME_SETTINGS } from './frames'
import {
  MAX_PRESETS,
  copyEditsToClipboard,
  deletePreset,
  hasClipboardEdits,
  loadPresets,
  pasteEditsFromClipboard,
  savePreset,
} from './editorPresets'

beforeEach(() => {
  window.localStorage.clear()
})

describe('savePreset / loadPresets / deletePreset', () => {
  it('saves and loads a named preset', () => {
    savePreset('My look', { ...DEFAULT_ADJUSTMENTS, contrast: 10 }, 'Vivid', 80)
    const presets = loadPresets()
    expect(presets).toHaveLength(1)
    expect(presets[0].name).toBe('My look')
    expect(presets[0].filterPreset).toBe('Vivid')
  })

  it('caps the list at MAX_PRESETS, dropping the oldest', () => {
    for (let i = 0; i < MAX_PRESETS + 5; i++) {
      savePreset(`Preset ${i}`, DEFAULT_ADJUSTMENTS, 'Original', 100)
    }
    const presets = loadPresets()
    expect(presets).toHaveLength(MAX_PRESETS)
    expect(presets[0].name).toBe('Preset 5')
    expect(presets[presets.length - 1].name).toBe(`Preset ${MAX_PRESETS + 4}`)
  })

  it('deletes a preset by id', () => {
    savePreset('A', DEFAULT_ADJUSTMENTS, 'Original', 100)
    const [preset] = loadPresets()
    const after = deletePreset(preset.id)
    expect(after).toHaveLength(0)
  })

  it('falls back to a default name when blank', () => {
    savePreset('   ', DEFAULT_ADJUSTMENTS, 'Original', 100)
    expect(loadPresets()[0].name).toBe('Untitled preset')
  })
})

describe('clipboard', () => {
  it('has nothing copied initially', () => {
    expect(hasClipboardEdits()).toBe(false)
    expect(pasteEditsFromClipboard()).toBeNull()
  })

  it('round trips a copy/paste', () => {
    copyEditsToClipboard({
      adjustments: { ...DEFAULT_ADJUSTMENTS, saturation: 25 },
      filterPreset: 'Cinematic',
      filterStrength: 60,
      frame: { ...DEFAULT_FRAME_SETTINGS, borderThickness: 10 },
    })
    expect(hasClipboardEdits()).toBe(true)
    const pasted = pasteEditsFromClipboard()
    expect(pasted?.adjustments.saturation).toBe(25)
    expect(pasted?.filterPreset).toBe('Cinematic')
    expect(pasted?.frame.borderThickness).toBe(10)
  })
})
