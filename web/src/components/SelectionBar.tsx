import { Icon } from './Icon'

export interface SelectionBarProps {
  count: number
  onShare: () => void
  onDownload: () => void
  onFavourite: () => void
  onLock: () => void
  onDelete: () => void
  onClear: () => void
}

export function SelectionBar({ count, onShare, onDownload, onFavourite, onLock, onDelete, onClear }: SelectionBarProps): React.ReactElement | null {
  if (count === 0) return null
  return (
    <div className="selection-bar">
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <button className="btn" onClick={onClear} aria-label="Clear selection">
          <Icon name="close" size={16} />
        </button>
        <strong>{count} selected</strong>
      </div>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        <button className="btn" onClick={onShare} aria-label="Share selected">
          <Icon name="share" size={16} /> Share
        </button>
        <button className="btn" onClick={onDownload} aria-label="Download selected">
          <Icon name="download" size={16} /> Download
        </button>
        <button className="btn" onClick={onFavourite} aria-label="Toggle favourite for selected">
          <Icon name="heart" size={16} /> Favourite
        </button>
        <button className="btn" onClick={onLock} aria-label="Lock selected">
          <Icon name="lock" size={16} /> Lock
        </button>
        <button className="btn btn-danger" onClick={onDelete} aria-label="Delete selected">
          <Icon name="trash" size={16} /> Delete
        </button>
      </div>
    </div>
  )
}
