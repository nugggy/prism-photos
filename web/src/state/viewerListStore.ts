import { create } from 'zustand'
import type { MediaItem } from '../plex/model'

/**
 * Holds the list of items the Viewer is currently browsing, set by whichever page
 * (Photos, Album, Favourites, Search, Locked) navigated into the viewer. Kept out of
 * the URL so item order and paging state do not need to be re-derived on every open.
 */
export interface ViewerListState {
  source: string
  items: MediaItem[]
  setList: (source: string, items: MediaItem[]) => void
}

export const useViewerListStore = create<ViewerListState>((set) => ({
  source: '',
  items: [],
  setList: (source, items) => set({ source, items }),
}))
