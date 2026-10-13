import { useToastStore } from '../state/toastStore'

/** Shows the current error or info toast from the toast store. */
export function Toaster(): React.ReactElement | null {
  const toast = useToastStore((s) => s.toast)
  const dismiss = useToastStore((s) => s.dismiss)
  if (!toast) return null
  return (
    <div className={`toast toast-message${toast.kind === 'error' ? ' toast-error' : ''}`} role={toast.kind === 'error' ? 'alert' : 'status'} key={toast.id}>
      <span>{toast.message}</span>
      <button className="btn" onClick={dismiss} aria-label="Dismiss">
        OK
      </button>
    </div>
  )
}
