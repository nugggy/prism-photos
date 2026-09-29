import { Icon } from './Icon'

export interface SelectionBarProps {
  count: number
  onShare?: () => void
  onDownload?: () => void
  onFavourite?: () => void
  onLock?: () => void
  onAddToAlbum?: () => void
  onRemoveFromAlbum?: () => void
  onDelete?: () => void
  onClear: () => void
}

export function SelectionBar({
  count,
  onShare,
  onDownload,
  onFavourite,
  onLock,
  onAddToAlbum,
  onRemoveFromAlbum,
  onDelete,
  onClear,
}: SelectionBarProps): React.ReactElement | null {
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
        {onAddToAlbum && (
          <button className="btn" onClick={onAddToAlbum} aria-label="Add selected to album">
            <Icon name="plus" size={16} /> Add to album
          </button>
        )}
        {onShare && (
          <button className="btn" onClick={onShare} aria-label="Share selected">
            <Icon name="share" size={16} /> Share
          </button>
        )}
        {onDownload && (
          <button className="btn" onClick={onDownload} aria-label="Download selected">
            <Icon name="download" size={16} /> Download
          </button>
        )}
        {onFavourite && (
          <button className="btn" onClick={onFavourite} aria-label="Toggle favourite for selected">
            <Icon name="heart" size={16} /> Favourite
          </button>
        )}
        {onLock && (
          <button className="btn" onClick={onLock} aria-label="Lock selected">
            <Icon name="lock" size={16} /> Lock
          </button>
        )}
        {onRemoveFromAlbum && (
          <button className="btn" onClick={onRemoveFromAlbum} aria-label="Remove selected from album">
            <Icon name="close" size={16} /> Remove from album
          </button>
        )}
        {onDelete && (
          <button className="btn btn-danger" onClick={onDelete} aria-label="Delete selected">
            <Icon name="trash" size={16} /> Delete
          </button>
        )}
      </div>
    </div>
  )
}
