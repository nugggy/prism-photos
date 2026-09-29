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
    'X-Plex-Product': 'Prism',
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
