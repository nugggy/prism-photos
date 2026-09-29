// IndexedDB cache of the last loaded timeline/albums so reloads are instant while a
// background refresh brings the data up to date. Uses idb-keyval for a tiny key-value store.

import { get, set, del, clear, createStore } from 'idb-keyval'
import type { Album, MediaItem } from '../plex/model'

const store = createStore('prism-cache', 'keyval')

function timelineKey(sectionKey: string): string {
  return `timeline:${sectionKey}`
}
function albumsKey(sectionKey: string, albumId: string | null): string {
  return `albums:${sectionKey}:${albumId ?? 'root'}`
}

export async function getCachedTimeline(sectionKey: string): Promise<MediaItem[] | undefined> {
  try {
    return await get<MediaItem[]>(timelineKey(sectionKey), store)
  } catch {
    return undefined
  }
}

export async function setCachedTimeline(sectionKey: string, items: MediaItem[]): Promise<void> {
  try {
    await set(timelineKey(sectionKey), items, store)
  } catch {
    /* best effort */
  }
}

export interface CachedAlbumChildren {
  albums: Album[]
  items: MediaItem[]
}

export async function getCachedAlbumChildren(sectionKey: string, albumId: string | null): Promise<CachedAlbumChildren | undefined> {
  try {
    return await get<CachedAlbumChildren>(albumsKey(sectionKey, albumId), store)
  } catch {
    return undefined
  }
}

export async function setCachedAlbumChildren(sectionKey: string, albumId: string | null, data: CachedAlbumChildren): Promise<void> {
  try {
    await set(albumsKey(sectionKey, albumId), data, store)
  } catch {
    /* best effort */
  }
}

/** Clears the IndexedDB timeline/album cache and any localStorage playback position caches. */
export async function clearCache(): Promise<void> {
  try {
    await clear(store)
  } catch {
    /* best effort */
  }
  try {
    const keys = Object.keys(localStorage).filter((k) => k.startsWith('prism.videoPosition.'))
    keys.forEach((k) => localStorage.removeItem(k))
  } catch {
    /* best effort */
  }
}

export async function deleteCachedTimeline(sectionKey: string): Promise<void> {
  try {
    await del(timelineKey(sectionKey), store)
  } catch {
    /* best effort */
  }
}
