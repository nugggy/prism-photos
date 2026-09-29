import { CROP_RATIOS } from '../../lib/crop'
import { Icon } from '../Icon'
import { Slider } from './Slider'

export interface CropPanelProps {
  ratioLabel: string | null
  imgAspect: number | null
  onRatioChange: (value: number | null, label: string) => void
  onRotate: () => void
  onFlipH: () => void
  onFlipV: () => void
  straighten: number
  onStraightenChange: (v: number) => void
  onStraightenCommit: () => void
}

export function CropPanel(props: CropPanelProps): React.ReactElement {
  const { ratioLabel, imgAspect, onRatioChange, onRotate, onFlipH, onFlipV, straighten, onStraightenChange, onStraightenCommit } = props

  return (
    <div>
      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Crop ratio</h3>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 16 }}>
        {CROP_RATIOS.map((r) => (
          <button key={r.label} type="button" className={`chip${ratioLabel === r.label ? ' active' : ''}`} onClick={() => onRatioChange(r.value, r.label)}>
            {r.label}
          </button>
        ))}
        {imgAspect && (
          <button type="button" className={`chip${ratioLabel === 'Original' ? ' active' : ''}`} onClick={() => onRatioChange(imgAspect, 'Original')}>
            Original
          </button>
        )}
      </div>

      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Rotate &amp; flip</h3>
      <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
        <button type="button" className="btn" onClick={onRotate} aria-label="Rotate 90 degrees">
          <Icon name="rotate" size={16} />
        </button>
        <button type="button" className="btn" onClick={onFlipH} aria-label="Flip horizontal">
          <Icon name="flip" size={16} />
          H
        </button>
        <button type="button" className="btn" onClick={onFlipV} aria-label="Flip vertical">
          <Icon name="flip" size={16} />
          V
        </button>
      </div>

      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Straighten</h3>
      <Slider label="Angle" value={straighten} min={-45} max={45} defaultValue={0} suffix="°" onChange={onStraightenChange} onCommit={onStraightenCommit} />
      <p className="muted" style={{ fontSize: 11, marginTop: -4 }}>
        Straightening automatically crops out the empty corners.
      </p>
    </div>
  )
}
