export interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ title, message, confirmLabel = 'Confirm', danger, onConfirm, onCancel }: ConfirmDialogProps): React.ReactElement {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200 }}
      onClick={onCancel}
    >
      <div className="card" style={{ width: 360, maxWidth: '90%' }} onClick={(e) => e.stopPropagation()}>
        <h2 style={{ marginTop: 0, fontSize: 18 }}>{title}</h2>
        <p className="muted">{message}</p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button className="btn" onClick={onCancel}>
            Cancel
          </button>
          <button className={danger ? 'btn btn-danger' : 'btn btn-primary'} onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
