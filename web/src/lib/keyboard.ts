/** True when a key event comes from somewhere the user types or picks values, so page shortcuts must not fire. */
export function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  if (target.isContentEditable) return true
  // jsdom does not implement isContentEditable, so also check the attribute.
  return target.closest('[contenteditable]:not([contenteditable="false"])') !== null
}

/** True when the event comes from inside an open modal dialog. */
export function isInsideDialog(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && target.closest('[role="dialog"]') !== null
}
