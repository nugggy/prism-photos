import { useMemo, useState } from 'react'
import { FILTER_PRESETS, applyToneAndColour, buildCssFilter, presetAdjustments, presetExtraCss, type FilterPreset } from '../../lib/imageFilters'
import type { EditorPreset } from '../../lib/editorPresets'
import { Icon } from '../Icon'
import { Slider } from './Slider'

export interface FiltersPanelProps {
  thumbSource: HTMLCanvasElement | null
  filterPreset: FilterPreset
  filterStrength: number
  onSelectPreset: (preset: FilterPreset) => void
  onStrengthChange: (strength: number) => void
  onStrengthCommit: () => void
  savedPresets: EditorPreset[]
  onSavePreset: (name: string) => void
  onApplySavedPreset: (preset: EditorPreset) => void
  onDeleteSavedPreset: (id: string) => void
}

function buildThumb(source: HTMLCanvasElement, preset: FilterPreset): string {
  const size = 84
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) return ''
  const adj = presetAdjustments(preset)
  ctx.filter = buildCssFilter(adj, presetExtraCss(preset))
  const srcSize = Math.min(source.width, source.height)
  const sx = (source.width - srcSize) / 2
  const sy = (source.height - srcSize) / 2
  ctx.drawImage(source, sx, sy, srcSize, srcSize, 0, 0, size, size)
  ctx.filter = 'none'
  try {
    const imageData = ctx.getImageData(0, 0, size, size)
    applyToneAndColour(imageData, adj)
    ctx.putImageData(imageData, 0, 0)
  } catch {
    // Cross-origin canvases can throw on getImageData; the CSS-only preview still looks fine.
  }
  try {
    return canvas.toDataURL('image/jpeg', 0.7)
  } catch {
    return ''
  }
}

export function FiltersPanel(props: FiltersPanelProps): React.ReactElement {
  const { thumbSource, filterPreset, filterStrength, onSelectPreset, onStrengthChange, onStrengthCommit, savedPresets, onSavePreset, onApplySavedPreset, onDeleteSavedPreset } = props
  const [presetName, setPresetName] = useState('')

  const thumbs = useMemo(() => {
    if (!thumbSource) return {} as Record<FilterPreset, string>
    const map: Partial<Record<FilterPreset, string>> = {}
    for (const preset of FILTER_PRESETS) {
      map[preset] = buildThumb(thumbSource, preset)
    }
    return map as Record<FilterPreset, string>
  }, [thumbSource])

  return (
    <div>
      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Filters</h3>
      <div className="filter-grid">
        {FILTER_PRESETS.map((preset) => (
          <button key={preset} type="button" className={`filter-thumb${filterPreset === preset ? ' active' : ''}`} onClick={() => onSelectPreset(preset)} aria-pressed={filterPreset === preset}>
            {thumbs[preset] ? <img src={thumbs[preset]} alt="" style={{ width: '100%', aspectRatio: '1', borderRadius: 8, border: `2px solid ${filterPreset === preset ? 'var(--gold)' : 'var(--border)'}`, objectFit: 'cover' }} /> : <span className="filter-thumb-fallback" />}
            <span>{preset}</span>
          </button>
        ))}
      </div>

      {filterPreset !== 'Original' && (
        <Slider label="Filter strength" value={filterStrength} min={0} max={100} defaultValue={100} suffix="%" onChange={onStrengthChange} onCommit={onStrengthCommit} />
      )}

      <h3 style={{ fontSize: 13, margin: '18px 0 8px' }}>My presets</h3>
      <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
        <input
          className="input"
          placeholder="Preset name"
          aria-label="New preset name"
          value={presetName}
          onChange={(e) => setPresetName(e.target.value)}
          maxLength={40}
        />
        <button
          type="button"
          className="btn"
          aria-label="Save current look as a preset"
          onClick={() => {
            if (!presetName.trim()) return
            onSavePreset(presetName.trim())
            setPresetName('')
          }}
        >
          <Icon name="save" size={16} />
        </button>
      </div>
      {savedPresets.length === 0 ? (
        <p className="muted" style={{ fontSize: 12 }}>
          No saved presets yet. Adjust the photo, then save the look above (up to 20).
        </p>
      ) : (
        <div className="preset-list">
          {savedPresets.map((preset) => (
            <div key={preset.id} className="preset-row">
              <button type="button" className="btn" style={{ border: 'none', background: 'none', padding: 0, flex: 1, textAlign: 'left' }} onClick={() => onApplySavedPreset(preset)}>
                {preset.name}
              </button>
              <button type="button" className="btn btn-danger" aria-label={`Delete preset ${preset.name}`} onClick={() => onDeleteSavedPreset(preset.id)}>
                <Icon name="trash" size={14} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
