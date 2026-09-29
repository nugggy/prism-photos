import { buildPlexHeaders } from './headers'
import type {
  PlexIdentityDto,
  PlexMediaContainerDto,
  PlexMetadataDto,
  PlexSectionsResponseDto,
} from './dto'
import { mapMetadataToAlbum, mapMetadataToMediaItem, type Album, type Library, type MediaItem } from './model'
import { dedupeById, mergeTimeline } from './timeline'
import type { ServerRef } from './urls'

export class PlexApiError extends Error {
  status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'PlexApiError'
    this.status = status
  }
}

function trim(baseUrl: string): string {
  return baseUrl.replace(/\/$/, '')
}

async function plexFetch<T>(
  server: ServerRef,
  path: string,
  init: RequestInit = {},
  fetchImpl: typeof fetch = fetch,
): Promise<T> {
  const url = `${trim(server.baseUrl)}${path}`
  const res = await fetchImpl(url, {
    ...init,
    headers: { ...buildPlexHeaders({ token: server.token }), ...(init.headers ?? {}) },
  })
  if (res.status === 401) {
    throw new PlexApiError('Session expired. Please sign in again.', 401)
  }
  if (res.status === 403) {
    throw new PlexApiError('The server refused this action (403).', 403)
  }
  if (!res.ok) {
    throw new PlexApiError(`Plex request failed (${res.status})`, res.status)
  }
  const text = await res.text()
  if (!text) return {} as T
  return JSON.parse(text) as T
}

/** Verifies a manually entered server URL + token, returning the friendly name. */
export async function verifyManualServer(server: ServerRef, fetchImpl: typeof fetch = fetch): Promise<string> {
  const dto = await plexFetch<PlexMediaContainerDto>(server, '/', {}, fetchImpl)
  return dto.MediaContainer.friendlyName ?? 'Plex Media Server'
}

export async function fetchIdentity(baseUrl: string, fetchImpl: typeof fetch = fetch): Promise<string> {
  const res = await fetchImpl(`${trim(baseUrl)}/identity`, { headers: { Accept: 'application/json' } })
  if (!res.ok) throw new PlexApiError(`Identity check failed (${res.status})`, res.status)
  const dto = (await res.json()) as PlexIdentityDto
  return dto.MediaContainer.machineIdentifier
}

export async function fetchSections(server: ServerRef, fetchImpl: typeof fetch = fetch): Promise<Library[]> {
  const dto = await plexFetch<PlexSectionsResponseDto>(server, '/library/sections', {}, fetchImpl)
  return dto.MediaContainer.Directory.filter((d) => d.type === 'photo').map((d) => ({
    key: d.key,
    title: d.title,
    type: d.type,
  }))
}

const PAGE_SIZE = 500

async function fetchAllPages(
  server: ServerRef,
  path: string,
  extraParams: Record<string, string>,
  fetchImpl: typeof fetch,
  paged = true,
): Promise<{ items: PlexMetadataDto[]; total: number }> {
  const results: PlexMetadataDto[] = []
  let start = 0
  let total = 0
  for (;;) {
    const params = new URLSearchParams(
      paged ? { ...extraParams, 'X-Plex-Container-Start': String(start), 'X-Plex-Container-Size': String(PAGE_SIZE) } : extraParams,
    )
    // Plex rejects percent encoded comparison operators (userRating%3E%3D10 is a 400), so restore them.
    const query = params.toString().replace(/%3E%3D/g, '>=').replace(/%3C%3D/g, '<=').replace(/%3E/g, '>').replace(/%3C/g, '<')
    const dto = await plexFetch<PlexMediaContainerDto>(server, `${path}?${query}`, {}, fetchImpl)
    const page = dto.MediaContainer.Metadata ?? []
    results.push(...page)
    total = dto.MediaContainer.totalSize ?? dto.MediaContainer.size ?? page.length
    start += page.length
    if (!paged || page.length === 0 || start >= total) break
  }
  return { items: results, total }
}

/** Fetches the full timeline (photos + clips) for a section, merged and sorted newest first. */
export async function fetchTimeline(
  server: ServerRef,
  sectionKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<MediaItem[]> {
  // Try the server side sort first and fall back to the default order (we sort locally anyway).
  // Plex 1.43 and later: the flat, paged timeline is all?clusterZoomLevel=1 (photos and videos together).
  // Plain type filters return nothing on those servers, so try this first.
  try {
    const flat = await fetchAllPages(server, `/library/sections/${sectionKey}/all`, { clusterZoomLevel: '1', sort: 'originallyAvailableAt:desc' }, fetchImpl)
    const flatItems = flat.items.filter(isMediaDto).map((d) => mapMetadataToMediaItem(d, sectionKey))
    if (flatItems.length > 0) return dedupeById(mergeTimeline(flatItems, []))
  } catch (e) {
    console.warn('Flat timeline request failed, falling back to type filters', e)
  }
  // Some servers answer a sorted or paged request with zero items and no error, so step down
  // through sorted+paged, unsorted+paged and unsorted+unpaged until something comes back.
  const fetchType = async (type: string): Promise<PlexMetadataDto[]> => {
    const path = `/library/sections/${sectionKey}/all`
    const attempts: Array<{ name: string; params: Record<string, string>; paged: boolean }> = [
      { name: 'sorted', params: { type, sort: 'originallyAvailableAt:desc' }, paged: true },
      { name: 'unsorted', params: { type }, paged: true },
      { name: 'unpaged', params: { type }, paged: false },
    ]
    let lastError: unknown = null
    for (const attempt of attempts) {
      try {
        const { items, total } = await fetchAllPages(server, path, attempt.params, fetchImpl, attempt.paged)
        if (items.length > 0) return items
        console.warn(`Type ${type} ${attempt.name} request returned 0 items (server total ${total}); trying the next approach`)
      } catch (e) {
        lastError = e
        console.warn(`Type ${type} ${attempt.name} request failed`, e)
      }
    }
    if (lastError) throw lastError
    return []
  }
  const [photoResult, clipResult] = await Promise.allSettled([fetchType('13'), fetchType('12')])
  if (photoResult.status === 'rejected' && clipResult.status === 'rejected') throw photoResult.reason
  if (photoResult.status === 'rejected') console.warn('Photos request failed', photoResult.reason)
  if (clipResult.status === 'rejected') console.warn('Videos request failed', clipResult.reason)
  const photos = (photoResult.status === 'fulfilled' ? photoResult.value : []).map((d) => mapMetadataToMediaItem(d, sectionKey))
  const clips = (clipResult.status === 'fulfilled' ? clipResult.value : []).map((d) => mapMetadataToMediaItem(d, sectionKey))
  // Second source, only when the type filters found nothing: walk the album folders, which does
  // not depend on numeric type filters.
  let walked: MediaItem[] = []
  if (photos.length === 0 && clips.length === 0) {
    try {
      walked = await walkAlbums(server, sectionKey, fetchImpl)
    } catch (e) {
      console.warn('Album walk failed', e)
    }
  }
  return dedupeById(mergeTimeline(photos, [...clips, ...walked]))
}

/** Albums: Plex reports them as photoalbum, or (1.43 and later) as photo entries whose key ends in /children. */
export function isAlbumDto(m: PlexMetadataDto): boolean {
  return m.type === 'photoalbum' || (m.key ?? '').endsWith('/children')
}

export function isMediaDto(m: PlexMetadataDto): boolean {
  if (isAlbumDto(m)) return false
  if (m.type === 'photo' || m.type === 'clip' || m.type === 'video') return true
  return (m.Media ?? []).some((media) => (media.Part ?? []).length > 0)
}

/** Recursively lists every photo and video by walking albums from the library root (4 requests at a time). */
export async function walkAlbums(server: ServerRef, sectionKey: string, fetchImpl: typeof fetch = fetch): Promise<MediaItem[]> {
  const items = new Map<string, MediaItem>()
  const seen = new Set<string>()
  const queue: string[] = []
  const absorb = (mc: PlexMediaContainerDto['MediaContainer']) => {
    const directory = ((mc as { Directory?: PlexMetadataDto[] }).Directory ?? []).map((d) => ({ ...d, type: d.type ?? 'photoalbum' }))
    const all = [...directory, ...(mc.Metadata ?? [])]
    for (const m of all) {
      if (isAlbumDto(m)) {
        const id = m.ratingKey ?? (m.key ?? '').match(/\/library\/metadata\/(\d+)/)?.[1]
        if (id && !seen.has(id)) { seen.add(id); queue.push(id) }
      } else if (isMediaDto(m)) {
        const item = mapMetadataToMediaItem(m, sectionKey)
        if (item.id) items.set(item.id, item)
      }
    }
  }
  const root = await plexFetch<PlexMediaContainerDto>(server, `/library/sections/${sectionKey}/all`, {}, fetchImpl)
  absorb(root.MediaContainer)
  let walked = 0
  while (queue.length > 0 && walked < 5000) {
    const batch = queue.splice(0, 4)
    const results = await Promise.allSettled(
      batch.map((id) => plexFetch<PlexMediaContainerDto>(server, `/library/metadata/${id}/children`, {}, fetchImpl)),
    )
    for (const r of results) if (r.status === 'fulfilled') absorb(r.value.MediaContainer)
    walked += batch.length
  }
  return [...items.values()]
}

export interface AlbumChildren {
  albums: Album[]
  items: MediaItem[]
}

/** Top level of a library: a mix of albums and loose items. */
export async function fetchAlbumRoot(
  server: ServerRef,
  sectionKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<AlbumChildren> {
  // type=14 lists albums on every server version we have seen; the plain listing is the fallback.
  try {
    const byType = await fetchAllPages(server, `/library/sections/${sectionKey}/all`, { type: '14' }, fetchImpl)
    if (byType.items.length > 0) return splitAlbumChildren(byType.items.map((d) => ({ ...d, type: 'photoalbum' })), sectionKey, null)
  } catch (e) {
    console.warn('type=14 album listing failed, using the plain listing', e)
  }
  const dto = await plexFetch<PlexMediaContainerDto>(server, `/library/sections/${sectionKey}/all`, {}, fetchImpl)
  // Some server versions list albums under Directory rather than Metadata; accept both.
  const directory = ((dto.MediaContainer as { Directory?: PlexMetadataDto[] }).Directory ?? []).map((d) => ({ ...d, type: d.type ?? 'photoalbum' }))
  const metadata = [...directory, ...(dto.MediaContainer.Metadata ?? [])]
  return splitAlbumChildren(metadata, sectionKey, null)
}

/** Children of an album: nested albums and items. */
export async function fetchAlbumChildren(
  server: ServerRef,
  sectionKey: string,
  albumId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<AlbumChildren> {
  const dto = await plexFetch<PlexMediaContainerDto>(server, `/library/metadata/${albumId}/children`, {}, fetchImpl)
  const metadata = dto.MediaContainer.Metadata ?? []
  return splitAlbumChildren(metadata, sectionKey, albumId)
}

function splitAlbumChildren(metadata: PlexMetadataDto[], sectionKey: string, parentId: string | null): AlbumChildren {
  const albums: Album[] = []
  const items: MediaItem[] = []
  const seen = new Set<string>()
  for (const m of metadata) {
    if (isAlbumDto(m)) {
      const album = mapMetadataToAlbum(m, sectionKey, parentId)
      if (album.id && !seen.has(album.id)) {
        seen.add(album.id)
        albums.push(album)
      }
    } else if (isMediaDto(m)) {
      items.push(mapMetadataToMediaItem(m, sectionKey))
    }
  }
  return { albums, items }
}

/** Everything rated 10 in Plex (favourites). */
export async function fetchFavourites(
  server: ServerRef,
  sectionKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<MediaItem[]> {
  // No type filter: on Plex 1.43 type filters return nothing, and this form returns photos and videos together.
  const path = `/library/sections/${sectionKey}/all`
  let found = (await fetchAllPages(server, path, { clusterZoomLevel: '1', 'userRating>': '10' }, fetchImpl, false)).items.filter(isMediaDto)
  if (found.length === 0) {
    const [photoDtos, clipDtos] = await Promise.all([
      fetchAllPages(server, path, { type: '13', 'userRating>': '10' }, fetchImpl).then((r) => r.items).catch(() => [] as PlexMetadataDto[]),
      fetchAllPages(server, path, { type: '12', 'userRating>': '10' }, fetchImpl).then((r) => r.items).catch(() => [] as PlexMetadataDto[]),
    ])
    found = [...photoDtos, ...clipDtos].filter(isMediaDto)
  }
  return dedupeById(mergeTimeline(found.map((d) => mapMetadataToMediaItem(d, sectionKey)), []))
}

export interface SearchFilters {
  query?: string
  tag?: string
  year?: number
}

export async function searchLibrary(
  server: ServerRef,
  sectionKey: string,
  filters: SearchFilters,
  fetchImpl: typeof fetch = fetch,
): Promise<MediaItem[]> {
  const results: MediaItem[] = []
  for (const type of ['13', '12'] as const) {
    const params: Record<string, string> = { type }
    let path = `/library/sections/${sectionKey}/all`
    if (filters.query) {
      path = `/library/sections/${sectionKey}/search`
      params.query = filters.query
    }
    if (filters.tag) params.tag = filters.tag
    if (filters.year) params.year = String(filters.year)
    try {
      const dtos = (await fetchAllPages(server, path, params, fetchImpl)).items.filter(isMediaDto)
      results.push(...dtos.map((d) => mapMetadataToMediaItem(d, sectionKey)))
    } catch (e) {
      console.warn(`Search for type ${type} failed`, e)
    }
  }
  if (results.length === 0 && filters.query) {
    // Title filter on the flat listing works on servers where the search endpoint does not.
    try {
      const byTitle = (await fetchAllPages(server, `/library/sections/${sectionKey}/all`, { clusterZoomLevel: '1', title: filters.query }, fetchImpl)).items.filter(isMediaDto)
      results.push(...byTitle.map((d) => mapMetadataToMediaItem(d, sectionKey)))
    } catch (e) {
      console.warn('Title filter search failed', e)
    }
  }
  return dedupeById(mergeTimeline(results, []))
}

export async function fetchMetadata(
  server: ServerRef,
  sectionKey: string,
  ratingKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<MediaItem | null> {
  const dto = await plexFetch<PlexMediaContainerDto>(server, `/library/metadata/${ratingKey}`, {}, fetchImpl)
  const meta = dto.MediaContainer.Metadata?.[0]
  return meta ? mapMetadataToMediaItem(meta, sectionKey) : null
}

/** Fetches an album's own metadata (title, description, cover), not its children. */
export async function fetchAlbumMeta(
  server: ServerRef,
  sectionKey: string,
  albumId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<Album | null> {
  const dto = await plexFetch<PlexMediaContainerDto>(server, `/library/metadata/${albumId}`, {}, fetchImpl)
  const meta = dto.MediaContainer.Metadata?.[0]
  return meta ? mapMetadataToAlbum(meta, sectionKey, meta.parentRatingKey ?? null) : null
}

export async function rateItem(
  server: ServerRef,
  ratingKey: string,
  favourite: boolean,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const params = new URLSearchParams({
    key: ratingKey,
    identifier: 'com.plexapp.plugins.library',
    rating: favourite ? '10' : '-1',
  })
  await plexFetch(server, `/:/rate?${params.toString()}`, { method: 'PUT' }, fetchImpl)
}

export interface EditMetadataOptions {
  sectionKey: string
  ratingKey: string
  type: '13' | '12' | '14'
  title?: string
  summary?: string
  addTags?: string[]
  removeTags?: string[]
}

export async function editMetadata(server: ServerRef, options: EditMetadataOptions, fetchImpl: typeof fetch = fetch): Promise<void> {
  const params = new URLSearchParams({ type: options.type, id: options.ratingKey })
  if (options.title !== undefined) {
    params.set('title.value', options.title)
    params.set('title.locked', '1')
  }
  if (options.summary !== undefined) {
    params.set('summary.value', options.summary)
    params.set('summary.locked', '1')
  }
  options.addTags?.forEach((tag, i) => {
    params.set(`tag[${i}].tag.tag`, tag)
  })
  if (options.addTags?.length) params.set('tag.locked', '1')
  options.removeTags?.forEach((tag) => {
    params.append('tag[].tag.tag-', tag)
  })
  await plexFetch(server, `/library/sections/${options.sectionKey}/all?${params.toString()}`, { method: 'PUT' }, fetchImpl)
}

export async function setAlbumCover(
  server: ServerRef,
  albumId: string,
  imageUrl: string,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const params = new URLSearchParams({ url: imageUrl })
  await plexFetch(server, `/library/metadata/${albumId}/posters?${params.toString()}`, { method: 'POST' }, fetchImpl)
}

export async function deleteItem(server: ServerRef, ratingKey: string, fetchImpl: typeof fetch = fetch): Promise<void> {
  await plexFetch(server, `/library/metadata/${ratingKey}`, { method: 'DELETE' }, fetchImpl)
}

// -----------------------------------------------------------------------------------
// "My albums": Plex photo playlists. Unlike folder albums (type=14) these are fully
// editable through the API and show up in every other Plex app too. See
// docs/plex-api.md "Verified against a real server" for the endpoints.
// -----------------------------------------------------------------------------------

function mapMetadataToPlaylistAlbum(dto: PlexMetadataDto): Album {
  return {
    id: dto.ratingKey,
    title: dto.title,
    summary: dto.summary ?? '',
    thumbPath: dto.composite ?? dto.thumb ?? '',
    itemCount: dto.leafCount ?? 0,
    addedAt: (dto.addedAt ?? 0) * 1000,
    sectionKey: '',
    parentId: null,
    isPlaylist: true,
    readOnly: dto.smart === true,
  }
}

/** Every "My album" (photo playlist), including the default smart Favorites playlist. */
export async function fetchMyAlbums(server: ServerRef, fetchImpl: typeof fetch = fetch): Promise<Album[]> {
  const dto = await plexFetch<PlexMediaContainerDto>(server, '/playlists?playlistType=photo', {}, fetchImpl)
  return (dto.MediaContainer.Metadata ?? []).map(mapMetadataToPlaylistAlbum)
}

export interface MyAlbumItem extends MediaItem {
  /** The playlist-scoped item id, needed to remove this item from the playlist. */
  playlistItemID: number
}

/** The items in a "My album", in playlist order. */
export async function fetchMyAlbumItems(
  server: ServerRef,
  sectionKey: string,
  playlistId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<MyAlbumItem[]> {
  const dto = await plexFetch<PlexMediaContainerDto>(server, `/playlists/${playlistId}/items`, {}, fetchImpl)
  return (dto.MediaContainer.Metadata ?? []).map((m) => ({
    ...mapMetadataToMediaItem(m, sectionKey),
    playlistItemID: m.playlistItemID ?? 0,
  }))
}

/** Builds the `server://` URI Plex expects to identify one or more library items when creating or adding to a playlist. */
export function buildPlaylistUri(machineIdentifier: string, itemIds: string[]): string {
  return `server://${machineIdentifier}/com.plexapp.plugins.library/library/metadata/${itemIds.join(',')}`
}

/** Creates a new "My album", optionally containing the given items (an empty array creates an empty album). */
export async function createMyAlbum(
  server: ServerRef,
  machineIdentifier: string,
  title: string,
  itemIds: string[],
  fetchImpl: typeof fetch = fetch,
): Promise<Album> {
  const params = new URLSearchParams({ type: 'photo', smart: '0', title })
  if (itemIds.length > 0) params.set('uri', buildPlaylistUri(machineIdentifier, itemIds))
  const dto = await plexFetch<PlexMediaContainerDto>(server, `/playlists?${params.toString()}`, { method: 'POST' }, fetchImpl)
  const meta = dto.MediaContainer.Metadata?.[0]
  if (!meta) throw new PlexApiError('The album was not created.')
  return mapMetadataToPlaylistAlbum(meta)
}

/** Adds items to an existing "My album". Plex silently ignores items already in it. */
export async function addToMyAlbum(
  server: ServerRef,
  machineIdentifier: string,
  playlistId: string,
  itemIds: string[],
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const params = new URLSearchParams({ uri: buildPlaylistUri(machineIdentifier, itemIds) })
  await plexFetch(server, `/playlists/${playlistId}/items?${params.toString()}`, { method: 'PUT' }, fetchImpl)
}

/** Maps the rating keys to remove to the playlist-scoped item ids Plex's remove endpoint needs. */
export function mapRatingKeysToPlaylistItemIds(items: PlexMetadataDto[], ratingKeys: string[]): number[] {
  const wanted = new Set(ratingKeys)
  return items
    .filter((m): m is PlexMetadataDto & { playlistItemID: number } => wanted.has(m.ratingKey) && m.playlistItemID !== undefined)
    .map((m) => m.playlistItemID)
}

/** Removes items from a "My album" by rating key (fetches current items to resolve playlistItemID). */
export async function removeFromMyAlbum(
  server: ServerRef,
  playlistId: string,
  itemIds: string[],
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const dto = await plexFetch<PlexMediaContainerDto>(server, `/playlists/${playlistId}/items`, {}, fetchImpl)
  const playlistItemIds = mapRatingKeysToPlaylistItemIds(dto.MediaContainer.Metadata ?? [], itemIds)
  await Promise.all(
    playlistItemIds.map((playlistItemID) =>
      plexFetch(server, `/playlists/${playlistId}/items/${playlistItemID}`, { method: 'DELETE' }, fetchImpl),
    ),
  )
}

export async function renameMyAlbum(
  server: ServerRef,
  playlistId: string,
  title: string,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const params = new URLSearchParams({ title })
  await plexFetch(server, `/playlists/${playlistId}?${params.toString()}`, { method: 'PUT' }, fetchImpl)
}

export async function deleteMyAlbum(server: ServerRef, playlistId: string, fetchImpl: typeof fetch = fetch): Promise<void> {
  await plexFetch(server, `/playlists/${playlistId}`, { method: 'DELETE' }, fetchImpl)
}
