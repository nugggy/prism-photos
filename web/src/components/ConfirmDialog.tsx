import { useDialogFocus } from '../lib/useDialogFocus'

export interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ title, message, confirmLabel = 'Confirm', danger, onConfirm, onCancel }: ConfirmDialogProps): React.ReactElement {
  const { ref, titleId } = useDialogFocus(onCancel)
  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200 }}
      onClick={onCancel}
    >
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="card"
        style={{ width: 360, maxWidth: '90%', outline: 'none' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id={titleId} style={{ marginTop: 0, fontSize: 18 }}>
          {title}
        </h2>
        <p className="muted">{message}</p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          {/* Destructive dialogs start on Cancel so a stray Enter does not confirm. */}
          <button className="btn" onClick={onCancel} data-autofocus={danger ? true : undefined}>
            Cancel
          </button>
          <button className={danger ? 'btn btn-danger' : 'btn btn-primary'} onClick={onConfirm} data-autofocus={danger ? undefined : true}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
