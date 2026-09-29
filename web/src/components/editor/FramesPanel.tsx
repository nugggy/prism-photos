import { DEFAULT_FRAME_SETTINGS, type FrameSettings } from '../../lib/frames'
import { MARKUP_COLORS } from '../../lib/markup'
import { Slider } from './Slider'

export interface FramesPanelProps {
  frame: FrameSettings
  onLiveChange: (patch: Partial<FrameSettings>) => void
  onCommit: () => void
  hasTakenAt: boolean
}

const BORDER_COLORS = ['#ffffff', '#111318', ...MARKUP_COLORS.slice(1)]

export function FramesPanel({ frame, onLiveChange, onCommit, hasTakenAt }: FramesPanelProps): React.ReactElement {
  return (
    <div>
      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Border</h3>
      <Slider label="Thickness" value={frame.borderThickness} min={0} max={100} defaultValue={DEFAULT_FRAME_SETTINGS.borderThickness} onChange={(v) => onLiveChange({ borderThickness: v })} onCommit={onCommit} />
      <div className="color-swatch-row">
        {BORDER_COLORS.map((c) => (
          <button
            key={c}
            type="button"
            className={`color-swatch${frame.borderColor === c ? ' active' : ''}`}
            style={{ background: c, borderColor: c === '#ffffff' ? 'var(--border)' : undefined }}
            aria-label={`Border colour ${c}`}
            onClick={() => {
              onLiveChange({ borderColor: c })
              onCommit()
            }}
          />
        ))}
      </div>

      <h3 style={{ fontSize: 13, margin: '14px 0 8px' }}>Corners</h3>
      <Slider label="Rounded corners" value={frame.cornerRadius} min={0} max={100} defaultValue={DEFAULT_FRAME_SETTINGS.cornerRadius} onChange={(v) => onLiveChange({ cornerRadius: v })} onCommit={onCommit} />

      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, margin: '10px 0' }}>
        <input type="checkbox" checked={frame.polaroid} onChange={(e) => { onLiveChange({ polaroid: e.target.checked }); onCommit() }} />
        Polaroid style
      </label>

      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, marginBottom: 10 }}>
        <input
          type="checkbox"
          checked={frame.dateStamp}
          disabled={!hasTakenAt}
          onChange={(e) => { onLiveChange({ dateStamp: e.target.checked }); onCommit() }}
        />
        Date stamp {!hasTakenAt && <span className="muted">(no date available)</span>}
      </label>

      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Watermark</h3>
      <input
        className="input"
        placeholder="Watermark text"
        aria-label="Watermark text"
        maxLength={60}
        value={frame.watermarkText}
        onChange={(e) => onLiveChange({ watermarkText: e.target.value })}
        onBlur={onCommit}
      />
    </div>
  )
}
