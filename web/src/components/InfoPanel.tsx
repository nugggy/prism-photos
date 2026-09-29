import { useState } from 'react'
import type { MediaItem } from '../plex/model'
import { formatDate, formatFileSize } from '../lib/format'
import { Icon } from './Icon'

export interface InfoPanelProps {
  item: MediaItem
  onClose: () => void
  onSaveSummary: (summary: string) => void
  onSaveTags: (tags: string[]) => void
}

export function InfoPanel({ item, onClose, onSaveSummary, onSaveTags }: InfoPanelProps): React.ReactElement {
  const [summary, setSummary] = useState(item.summary)
  const [tagInput, setTagInput] = useState('')
  const [tags, setTags] = useState(item.tags)

  const addTag = () => {
    const t = tagInput.trim()
    if (!t || tags.includes(t)) return
    const next = [...tags, t]
    setTags(next)
    setTagInput('')
    onSaveTags(next)
  }
  const removeTag = (t: string) => {
    const next = tags.filter((x) => x !== t)
    setTags(next)
    onSaveTags(next)
  }

  return (
    <div
      className="card"
      style={{ position: 'absolute', top: 0, right: 0, bottom: 0, width: 340, maxWidth: '90%', overflowY: 'auto', borderRadius: 0, zIndex: 30 }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <h2 style={{ margin: 0, fontSize: 16 }}>Info</h2>
        <button className="btn" onClick={onClose} aria-label="Close info">
          <Icon name="close" size={16} />
        </button>
      </div>

      <label style={{ fontSize: 12 }} className="muted">
        Description
      </label>
      <textarea
        className="input"
        rows={3}
        value={summary}
        onChange={(e) => setSummary(e.target.value)}
        onBlur={() => onSaveSummary(summary)}
        style={{ marginBottom: 12, resize: 'vertical' }}
      />

      <label style={{ fontSize: 12 }} className="muted">
        Tags
      </label>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 6 }}>
        {tags.map((t) => (
          <span key={t} className="chip active" onClick={() => removeTag(t)} style={{ cursor: 'pointer' }}>
            {t} ×
          </span>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
        <input className="input" value={tagInput} onChange={(e) => setTagInput(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && addTag()} placeholder="Add tag" />
        <button className="btn" onClick={addTag}>
          Add
        </button>
      </div>

      <dl style={{ fontSize: 13 }}>
        <Row label="Taken" value={formatDate(item.takenAt)} />
        <Row label="Dimensions" value={`${item.width} × ${item.height}`} />
        <Row label="Size" value={formatFileSize(item.fileSize)} />
        <Row label="Path" value={item.filePath} />
        {item.place && <Row label="Place" value={item.place} />}
        {item.country && <Row label="Country" value={item.country} />}
        {item.exif.make && <Row label="Camera" value={`${item.exif.make} ${item.exif.model ?? ''}`} />}
        {item.exif.lens && <Row label="Lens" value={item.exif.lens} />}
        {item.exif.aperture && <Row label="Aperture" value={item.exif.aperture} />}
        {item.exif.exposure && <Row label="Exposure" value={item.exif.exposure} />}
        {item.exif.iso && <Row label="ISO" value={String(item.exif.iso)} />}
        {item.exif.videoCodec && <Row label="Video codec" value={item.exif.videoCodec} />}
        {item.exif.audioCodec && <Row label="Audio codec" value={item.exif.audioCodec} />}
      </dl>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }): React.ReactElement {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '4px 0', borderBottom: '1px solid var(--border)' }}>
      <dt className="muted">{label}</dt>
      <dd style={{ margin: 0, textAlign: 'right', wordBreak: 'break-word' }}>{value}</dd>
    </div>
  )
}
