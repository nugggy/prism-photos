// URL builders for images, video, transcode and download. Pure functions, unit tested.

export interface ServerRef {
  baseUrl: string // e.g. https://192-168-1-20.abcdef.plex.direct:32400 (no trailing slash)
  token: string
}

function withTrailingTrim(baseUrl: string): string {
  return baseUrl.replace(/\/$/, '')
}

/** Thumbnail via the server transcoder, used for grid cells and viewer preview frames. */
export function buildThumbUrl(server: ServerRef, thumbPath: string, width: number, height: number): string {
  const base = withTrailingTrim(server.baseUrl)
  const params = new URLSearchParams({
    width: String(width),
    height: String(height),
    minSize: '1',
    upscale: '1',
    url: thumbPath,
    'X-Plex-Token': server.token,
  })
  return `${base}/photo/:/transcode?${params.toString()}`
}

/** Long edge, in pixels, of the transcoded image shown in the viewer. Preloading uses the same size so the cache hits. */
export const VIEWER_IMAGE_SIZE = 2000

/** Grid thumbnail widths. A few fixed sizes keep the browser and server caches effective. */
export const THUMB_SIZES = [200, 320, 480, 720, 960] as const

/** Picks the smallest thumbnail size that covers a cell of the given CSS size on this display. */
export function pickThumbSize(cellCssPx: number, devicePixelRatio = 1): number {
  const needed = cellCssPx * Math.max(1, devicePixelRatio)
  return THUMB_SIZES.find((s) => s >= needed) ?? THUMB_SIZES[THUMB_SIZES.length - 1]
}

/** Full resolution original file, optionally as a forced download. */
export function buildOriginalUrl(server: ServerRef, partKey: string, download = false): string {
  const base = withTrailingTrim(server.baseUrl)
  const params = new URLSearchParams({ 'X-Plex-Token': server.token })
  if (download) params.set('download', '1')
  return `${base}${partKey}?${params.toString()}`
}

/** Direct play URL for video, identical shape to the original file URL. */
export function buildDirectPlayUrl(server: ServerRef, partKey: string): string {
  return buildOriginalUrl(server, partKey, false)
}

export function buildDownloadUrl(server: ServerRef, partKey: string): string {
  return buildOriginalUrl(server, partKey, true)
}

export interface HlsTranscodeOptions {
  ratingKey: string
  session: string
  clientIdentifier: string
  platform?: string
  maxVideoBitrate?: number
  videoResolution?: string
  videoQuality?: number
}

/** HLS transcode start URL, used as a fallback when direct play fails. */
export function buildHlsTranscodeUrl(server: ServerRef, options: HlsTranscodeOptions): string {
  const base = withTrailingTrim(server.baseUrl)
  const path = `/library/metadata/${options.ratingKey}`
  const params = new URLSearchParams({
    path,
    mediaIndex: '0',
    partIndex: '0',
    protocol: 'hls',
    fastSeek: '1',
    directPlay: '0',
    directStream: '1',
    videoQuality: String(options.videoQuality ?? 100),
    maxVideoBitrate: String(options.maxVideoBitrate ?? 20000),
    videoResolution: options.videoResolution ?? '1920x1080',
    session: options.session,
    'X-Plex-Client-Identifier': options.clientIdentifier,
    'X-Plex-Platform': options.platform ?? 'Web',
    'X-Plex-Product': 'Plex Gallery',
    'X-Plex-Token': server.token,
  })
  return `${base}/video/:/transcode/universal/start.m3u8?${params.toString()}`
}

export function buildHlsTranscodeStopUrl(server: ServerRef, session: string): string {
  const base = withTrailingTrim(server.baseUrl)
  const params = new URLSearchParams({ session, 'X-Plex-Token': server.token })
  return `${base}/video/:/transcode/universal/stop?${params.toString()}`
}

/** The Plex web app deep link for a metadata item, used for "Open in Plex" and share links. */
export function buildPlexWebLink(machineId: string, ratingKey: string): string {
  return `https://app.plex.tv/desktop/#!/server/${machineId}/details?key=${encodeURIComponent(
    `/library/metadata/${ratingKey}`,
  )}`
}

export function buildIdentityUrl(baseUrl: string): string {
  return `${withTrailingTrim(baseUrl)}/identity`
}
