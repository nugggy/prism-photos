import { ADJUSTMENT_LABELS, ADJUSTMENT_RANGES, DEFAULT_ADJUSTMENTS, type Adjustments } from '../../lib/imageFilters'
import { Slider } from './Slider'

export interface AdjustPanelProps {
  adjustments: Adjustments
  onLiveChange: (adjustments: Adjustments) => void
  onCommit: () => void
  onAutoEnhance: () => void
}

const GROUPS: { title: string; keys: (keyof Adjustments)[] }[] = [
  { title: 'Light', keys: ['brightness', 'exposure', 'contrast', 'highlights', 'shadows', 'whites', 'blacks'] },
  { title: 'Colour', keys: ['saturation', 'vibrance', 'warmth', 'tint', 'hue'] },
  { title: 'Detail', keys: ['sharpness', 'clarity', 'blur', 'dehaze'] },
  { title: 'Effects', keys: ['vignetteStrength', 'vignetteSoftness', 'grain', 'fade'] },
]

export function AdjustPanel({ adjustments, onLiveChange, onCommit, onAutoEnhance }: AdjustPanelProps): React.ReactElement {
  const setField = (key: keyof Adjustments, value: number) => {
    onLiveChange({ ...adjustments, [key]: value })
  }

  return (
    <div>
      <button type="button" className="btn" style={{ width: '100%', marginBottom: 14, justifyContent: 'center' }} onClick={onAutoEnhance}>
        Auto enhance
      </button>
      {GROUPS.map((group) => (
        <div key={group.title} style={{ marginBottom: 16 }}>
          <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>{group.title}</h3>
          {group.keys.map((key) => {
            const [min, max] = ADJUSTMENT_RANGES[key]
            return (
              <Slider
                key={key}
                label={ADJUSTMENT_LABELS[key]}
                value={adjustments[key]}
                min={min}
                max={max}
                defaultValue={DEFAULT_ADJUSTMENTS[key]}
                onChange={(v) => setField(key, v)}
                onCommit={onCommit}
              />
            )
          })}
        </div>
      ))}
    </div>
  )
}
