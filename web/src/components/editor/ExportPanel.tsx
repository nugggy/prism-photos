import { Icon } from '../Icon'
import { Slider } from './Slider'

export type ExportFormat = 'jpeg' | 'png' | 'webp'
export type ExportSize = 'original' | 2048 | 1080

export interface ExportPanelProps {
  format: ExportFormat
  onFormatChange: (format: ExportFormat) => void
  quality: number
  onQualityChange: (quality: number) => void
  size: ExportSize
  onSizeChange: (size: ExportSize) => void
  onDownload: () => void
  onShare: () => void
  exporting: boolean
  canShare: boolean
  onCopyEdits: () => void
  onPasteEdits: () => void
  hasClipboard: boolean
  copiedMessage: string | null
}

const FORMATS: { id: ExportFormat; label: string }[] = [
  { id: 'jpeg', label: 'JPEG' },
  { id: 'png', label: 'PNG' },
  { id: 'webp', label: 'WebP' },
]

const SIZES: { id: ExportSize; label: string }[] = [
  { id: 'original', label: 'Original' },
  { id: 2048, label: '2048px' },
  { id: 1080, label: '1080px' },
]

export function ExportPanel(props: ExportPanelProps): React.ReactElement {
  const { format, onFormatChange, quality, onQualityChange, size, onSizeChange, onDownload, onShare, exporting, canShare, onCopyEdits, onPasteEdits, hasClipboard, copiedMessage } = props

  return (
    <div>
      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Format</h3>
      <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
        {FORMATS.map((f) => (
          <button key={f.id} type="button" className={`chip${format === f.id ? ' active' : ''}`} onClick={() => onFormatChange(f.id)}>
            {f.label}
          </button>
        ))}
      </div>

      {format !== 'png' && <Slider label="Quality" value={Math.round(quality * 100)} min={10} max={100} defaultValue={92} suffix="%" onChange={(v) => onQualityChange(v / 100)} />}

      <h3 style={{ fontSize: 13, margin: '14px 0 8px' }}>Size</h3>
      <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
        {SIZES.map((s) => (
          <button key={s.id} type="button" className={`chip${size === s.id ? ' active' : ''}`} onClick={() => onSizeChange(s.id)}>
            {s.label}
          </button>
        ))}
      </div>

      <button type="button" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center', marginBottom: 8 }} onClick={onDownload} disabled={exporting}>
        <Icon name="download" size={16} />
        {exporting ? 'Exporting' : 'Download'}
      </button>
      {canShare && (
        <button type="button" className="btn" style={{ width: '100%', justifyContent: 'center', marginBottom: 16 }} onClick={onShare} disabled={exporting}>
          <Icon name="share" size={16} />
          Share
        </button>
      )}

      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Copy edits</h3>
      <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
        Copy the current adjustments, filter and frame, then paste them onto another photo.
      </p>
      <div style={{ display: 'flex', gap: 6 }}>
        <button type="button" className="btn" style={{ flex: 1, justifyContent: 'center' }} onClick={onCopyEdits}>
          <Icon name="clipboard" size={16} />
          Copy edits
        </button>
        <button type="button" className="btn" style={{ flex: 1, justifyContent: 'center' }} onClick={onPasteEdits} disabled={!hasClipboard}>
          <Icon name="clipboard" size={16} />
          Paste edits
        </button>
      </div>
      {copiedMessage && (
        <p className="muted" style={{ fontSize: 12 }}>
          {copiedMessage}
        </p>
      )}
    </div>
  )
}
