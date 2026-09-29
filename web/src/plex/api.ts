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
): Promise<PlexMetadataDto[]> {
  const results: PlexMetadataDto[] = []
  let start = 0
  for (;;) {
    const params = new URLSearchParams({
      ...extraParams,
      'X-Plex-Container-Start': String(start),
      'X-Plex-Container-Size': String(PAGE_SIZE),
    })
    const dto = await plexFetch<PlexMediaContainerDto>(server, `${path}?${params.toString()}`, {}, fetchImpl)
    const page = dto.MediaContainer.Metadata ?? []
    results.push(...page)
    const total = dto.MediaContainer.totalSize ?? dto.MediaContainer.size ?? page.length
    start += page.length
    if (page.length === 0 || start >= total) break
  }
  return results
}

/** Fetches the full timeline (photos + clips) for a section, merged and sorted newest first. */
export async function fetchTimeline(
  server: ServerRef,
  sectionKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<MediaItem[]> {
  const [photoDtos, clipDtos] = await Promise.all([
    fetchAllPages(server, `/library/sections/${sectionKey}/all`, { type: '13', sort: 'originallyAvailableAt:desc' }, fetchImpl),
    fetchAllPages(server, `/library/sections/${sectionKey}/all`, { type: '12', sort: 'originallyAvailableAt:desc' }, fetchImpl),
  ])
  const photos = photoDtos.map((d) => mapMetadataToMediaItem(d, sectionKey))
  const clips = clipDtos.map((d) => mapMetadataToMediaItem(d, sectionKey))
  return dedupeById(mergeTimeline(photos, clips))
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
  const dto = await plexFetch<PlexMediaContainerDto>(server, `/library/sections/${sectionKey}/all`, {}, fetchImpl)
  const metadata = dto.MediaContainer.Metadata ?? []
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
  for (const m of metadata) {
    if (m.type === 'photoalbum') {
      albums.push(mapMetadataToAlbum(m, sectionKey, parentId))
    } else if (m.type === 'photo' || m.type === 'clip') {
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
  const [photoDtos, clipDtos] = await Promise.all([
    fetchAllPages(server, `/library/sections/${sectionKey}/all`, { type: '13', 'userRating>=': '10' }, fetchImpl),
    fetchAllPages(server, `/library/sections/${sectionKey}/all`, { type: '12', 'userRating>=': '10' }, fetchImpl),
  ])
  const photos = photoDtos.map((d) => mapMetadataToMediaItem(d, sectionKey))
  const clips = clipDtos.map((d) => mapMetadataToMediaItem(d, sectionKey))
  return dedupeById(mergeTimeline(photos, clips))
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
    const dtos = await fetchAllPages(server, path, params, fetchImpl)
    results.push(...dtos.map((d) => mapMetadataToMediaItem(d, sectionKey)))
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
