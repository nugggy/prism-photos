import { useEffect, useId, useRef } from 'react'

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"]), [contenteditable="true"]'

function focusableIn(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((el) => !el.hasAttribute('inert') && el.getAttribute('aria-hidden') !== 'true')
}

export interface DialogFocus<T extends HTMLElement> {
  /** Attach to the dialog panel (the element that should contain focus). */
  ref: React.RefObject<T | null>
  /** Use as the id of the dialog heading and pass to aria-labelledby. */
  titleId: string
}

/**
 * Modal dialog focus handling: moves focus into the dialog on open (the element marked
 * data-autofocus, else the first focusable element, else the panel itself), keeps Tab and
 * Shift+Tab inside it, closes on Escape, and gives focus back to whatever opened it on close.
 * Escape is stopped here so page level shortcuts (such as the viewer closing) do not also fire.
 */
export function useDialogFocus<T extends HTMLElement = HTMLDivElement>(onClose: () => void): DialogFocus<T> {
  const ref = useRef<T>(null)
  const titleId = useId()
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const panel = ref.current
    if (!panel) return
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null

    const initial = panel.querySelector<HTMLElement>('[data-autofocus]') ?? focusableIn(panel)[0] ?? panel
    initial.focus()

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        e.stopPropagation()
        onCloseRef.current()
        return
      }
      if (e.key !== 'Tab') return
      const items = focusableIn(panel)
      if (items.length === 0) {
        e.preventDefault()
        panel.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      if (e.shiftKey && (active === first || active === panel || !panel.contains(active))) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && (active === last || !panel.contains(active))) {
        e.preventDefault()
        first.focus()
      }
    }
    panel.addEventListener('keydown', onKeyDown)
    return () => {
      panel.removeEventListener('keydown', onKeyDown)
      if (opener && opener.isConnected) opener.focus()
    }
  }, [])

  return { ref, titleId }
}
