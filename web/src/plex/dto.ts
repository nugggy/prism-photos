// Raw JSON shapes returned by the Plex API, as documented in docs/plex-api.md.
// Kept intentionally loose (most fields optional) since Plex omits fields liberally
// depending on server version and item type.

export interface PlexPinDto {
  id: number
  code: string
  expiresAt: string
  authToken: string | null
}

export interface PlexUserDto {
  id: number
  uuid: string
  username: string
  email: string
  thumb: string
  title: string
}

export interface PlexConnectionDto {
  protocol: string
  address: string
  port: number
  uri: string
  local: boolean
  relay: boolean
  IPv6: boolean
}

export interface PlexResourceDto {
  name: string
  product: string
  productVersion: string
  platform: string
  clientIdentifier: string
  provides: string
  owned: boolean
  accessToken?: string
  publicAddress?: string
  httpsRequired?: boolean
  connections: PlexConnectionDto[]
}

export interface PlexIdentityDto {
  MediaContainer: {
    machineIdentifier: string
    version?: string
  }
}

export interface PlexDirectoryDto {
  key: string
  type: string
  title: string
  agent?: string
  scanner?: string
  uuid?: string
  thumb?: string
  updatedAt?: number
  Location?: { id: number; path: string }[]
}

export interface PlexSectionsResponseDto {
  MediaContainer: {
    Directory: PlexDirectoryDto[]
  }
}

export interface PlexPartDto {
  id: number
  key: string
  file: string
  size?: number
  container?: string
  duration?: number
}

export interface PlexMediaDto {
  id: number
  width?: number
  height?: number
  aspectRatio?: number
  container?: string
  aperture?: string
  exposure?: string
  iso?: number
  lens?: string
  make?: string
  model?: string
  videoCodec?: string
  audioCodec?: string
  duration?: number
  videoResolution?: string
  originallyAvailableAt?: string
  Part: PlexPartDto[]
}

export interface PlexTagDto {
  tag: string
}

export interface PlexMetadataDto {
  ratingKey: string
  key: string
  guid?: string
  type: string
  title: string
  summary?: string
  index?: number
  year?: number
  thumb?: string
  composite?: string
  leafCount?: number
  originallyAvailableAt?: string
  addedAt?: number
  updatedAt?: number
  userRating?: number
  createdAtAccuracy?: string
  createdAtTZOffset?: string
  parentRatingKey?: string
  parentKey?: string
  parentTitle?: string
  duration?: number
  Media?: PlexMediaDto[]
  Tag?: PlexTagDto[]
  Country?: PlexTagDto[]
  Place?: PlexTagDto[]
  // Photo playlists ("My albums").
  smart?: boolean
  playlistType?: string
  playlistItemID?: number
}

export interface PlexMediaContainerDto {
  MediaContainer: {
    size?: number
    totalSize?: number
    offset?: number
    Metadata?: PlexMetadataDto[]
    machineIdentifier?: string
    friendlyName?: string
  }
}
