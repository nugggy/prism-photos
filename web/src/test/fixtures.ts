import type { MediaItem } from '../plex/model'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'

export function makeItem(id: string, overrides: Partial<MediaItem> = {}): MediaItem {
  return {
    id,
    kind: 'photo',
    title: `Photo ${id}`,
    summary: '',
    takenAt: Date.UTC(2026, 0, 1) - Number(id) * 86_400_000,
    addedAt: 0,
    width: 4000,
    height: 3000,
    thumbPath: `/library/metadata/${id}/thumb/1700000000`,
    partKey: `/library/parts/${id}/file.jpg`,
    durationMs: 0,
    favourite: false,
    albumId: null,
    albumTitle: null,
    exif: {},
    fileSize: 0,
    filePath: '',
    tags: [],
    place: null,
    country: null,
    sectionKey: '1',
    ...overrides,
  }
}

/** Puts the session store into a connected state against a fake server. */
export function connectFakeServer(): void {
  useSessionStore.setState({
    serverToken: 'server-token',
    accountToken: null,
    activeConnection: { baseUrl: 'http://plex.test:32400', kind: 'manual' },
    selectedSectionKey: '1',
    machineId: 'machine-1',
  })
}

export function resetLockStore(): void {
  useLockStore.setState({ passcode: null, lockedItemIds: [], lockedAlbumIds: [], unlocked: false, lastActivityAt: Date.now() })
}
