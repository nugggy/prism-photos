import type { PlexDirectoryDto, PlexMetadataDto } from './dto'

export type MediaKind = 'photo' | 'video'

export interface ExifInfo {
  aperture?: string
  exposure?: string
  iso?: number
  lens?: string
  make?: string
  model?: string
  videoCodec?: string
  audioCodec?: string
  videoResolution?: string
}

export interface MediaItem {
  id: string
  kind: MediaKind
  title: string
  summary: string
  takenAt: number
  addedAt: number
  width: number
  height: number
  thumbPath: string
  partKey: string
  durationMs: number
  favourite: boolean
  albumId: string | null
  albumTitle: string | null
  exif: ExifInfo
  fileSize: number
  filePath: string
  tags: string[]
  place: string | null
  country: string | null
  sectionKey: string
}

export interface Album {
  id: string
  title: string
  summary: string
  thumbPath: string
  itemCount: number
  addedAt: number
  sectionKey: string
  parentId: string | null
  /** True for "My albums" (Plex photo playlists), as opposed to a folder album. */
  isPlaylist?: boolean
  /** True for a smart playlist, which cannot be edited through the API. */
  readOnly?: boolean
}

export interface Library {
  key: string
  title: string
  type: string
}

/** YYYY-MM-DD parsed as local midnight, per docs/plex-api.md. */
export function parseOriginallyAvailableAt(value: string | undefined): number | null {
  if (!value) return null
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return null
  const [, y, m, d] = match
  const date = new Date(Number(y), Number(m) - 1, Number(d), 0, 0, 0, 0)
  const time = date.getTime()
  return Number.isNaN(time) ? null : time
}

export function mapDirectoryToLibrary(dto: PlexDirectoryDto): Library {
  return { key: dto.key, title: dto.title, type: dto.type }
}

/** Maps a Plex Metadata item (type photo/clip) into the app's MediaItem model. */
export function mapMetadataToMediaItem(dto: PlexMetadataDto, sectionKey: string): MediaItem {
  const media = dto.Media?.[0]
  const part = media?.Part?.[0]
  const isVideo = dto.type === 'clip' || dto.type === 'video' || dto.type === 'movie' || dto.type === 'episode' || Boolean(media?.videoCodec) || (dto.duration ?? 0) > 0
  const takenAt = parseOriginallyAvailableAt(dto.originallyAvailableAt) ?? (dto.addedAt ?? 0) * 1000
  return {
    id: dto.ratingKey,
    kind: isVideo ? 'video' : 'photo',
    title: dto.title,
    summary: dto.summary ?? '',
    takenAt,
    addedAt: (dto.addedAt ?? 0) * 1000,
    width: media?.width ?? 0,
    height: media?.height ?? 0,
    thumbPath: dto.thumb ?? '',
    partKey: part?.key ?? '',
    durationMs: dto.duration ?? media?.duration ?? 0,
    favourite: (dto.userRating ?? 0) >= 10,
    albumId: dto.parentRatingKey ?? null,
    albumTitle: dto.parentTitle ?? null,
    exif: {
      aperture: media?.aperture,
      exposure: media?.exposure,
      iso: media?.iso,
      lens: media?.lens,
      make: media?.make,
      model: media?.model,
      videoCodec: media?.videoCodec,
      audioCodec: media?.audioCodec,
      videoResolution: media?.videoResolution,
    },
    fileSize: part?.size ?? 0,
    filePath: part?.file ?? '',
    tags: (dto.Tag ?? []).map((t) => t.tag),
    place: dto.Place?.[0]?.tag ?? null,
    country: dto.Country?.[0]?.tag ?? null,
    sectionKey,
  }
}

/** Maps a Plex Metadata item (type photoalbum) into the app's Album model. */
export function mapMetadataToAlbum(dto: PlexMetadataDto, sectionKey: string, parentId: string | null): Album {
  return {
    id: dto.ratingKey,
    title: dto.title,
    summary: dto.summary ?? '',
    thumbPath: dto.composite ?? dto.thumb ?? '',
    itemCount: dto.leafCount ?? 0,
    addedAt: (dto.addedAt ?? 0) * 1000,
    sectionKey,
    parentId,
  }
}
