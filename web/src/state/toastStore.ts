import { create } from 'zustand'

export type ToastKind = 'error' | 'info'

export interface Toast {
  id: number
  message: string
  kind: ToastKind
}

export interface ToastState {
  toast: Toast | null
  show: (message: string, kind?: ToastKind) => void
  dismiss: () => void
}

const DISPLAY_MS = 6000
let nextId = 1
let timer: ReturnType<typeof setTimeout> | undefined

/** One toast at a time: a newer message replaces the current one. */
export const useToastStore = create<ToastState>((set) => ({
  toast: null,
  show: (message, kind = 'error') => {
    const id = nextId++
    set({ toast: { id, message, kind } })
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => set((s) => (s.toast?.id === id ? { toast: null } : s)), DISPLAY_MS)
  },
  dismiss: () => set({ toast: null }),
}))

/** Shows a toast from outside React. */
export function showToast(message: string, kind: ToastKind = 'error'): void {
  useToastStore.getState().show(message, kind)
}

/** Turns an unknown error into a message, falling back to the given text. */
export function errorMessage(e: unknown, fallback: string): string {
  return e instanceof Error && e.message ? e.message : fallback
}
