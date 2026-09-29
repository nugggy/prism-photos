import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type AlbumSortOrder = 'newest' | 'oldest' | 'titleAsc' | 'titleDesc'

export interface AlbumPrefs {
  coverItemId?: string
  accentColor?: string
  sortOrder?: AlbumSortOrder
}

export interface AlbumPrefsState {
  prefs: Record<string, AlbumPrefs>
  getPrefs: (albumId: string) => AlbumPrefs
  setCover: (albumId: string, itemId: string) => void
  setAccentColor: (albumId: string, color: string) => void
  setSortOrder: (albumId: string, order: AlbumSortOrder) => void
}

export const useAlbumPrefsStore = create<AlbumPrefsState>()(
  persist(
    (set, get) => ({
      prefs: {},
      getPrefs: (albumId) => get().prefs[albumId] ?? {},
      setCover: (albumId, itemId) =>
        set((s) => ({ prefs: { ...s.prefs, [albumId]: { ...s.prefs[albumId], coverItemId: itemId } } })),
      setAccentColor: (albumId, color) =>
        set((s) => ({ prefs: { ...s.prefs, [albumId]: { ...s.prefs[albumId], accentColor: color } } })),
      setSortOrder: (albumId, order) =>
        set((s) => ({ prefs: { ...s.prefs, [albumId]: { ...s.prefs[albumId], sortOrder: order } } })),
    }),
    { name: 'prism.albumPrefs' },
  ),
)
