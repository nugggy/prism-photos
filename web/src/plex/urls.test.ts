import { describe, expect, it } from 'vitest'
import {
  buildDirectPlayUrl,
  buildDownloadUrl,
  buildHlsTranscodeStopUrl,
  buildHlsTranscodeUrl,
  buildOriginalUrl,
  buildPlexWebLink,
  buildThumbUrl,
} from './urls'

const server = { baseUrl: 'https://192-168-1-20.abcdef.plex.direct:32400/', token: 'TOKEN123' }

describe('buildThumbUrl', () => {
  it('builds a transcoder thumbnail URL with dimensions and token', () => {
    const url = buildThumbUrl(server, '/library/metadata/1234/thumb/1700000000', 400, 400)
    expect(url).toBe(
      'https://192-168-1-20.abcdef.plex.direct:32400/photo/:/transcode?width=400&height=400&minSize=1&upscale=1&url=%2Flibrary%2Fmetadata%2F1234%2Fthumb%2F1700000000&X-Plex-Token=TOKEN123',
    )
  })
})

describe('buildOriginalUrl', () => {
  it('builds the original file URL without download', () => {
    const url = buildOriginalUrl(server, '/library/parts/5678/1700000000/file.jpg')
    expect(url).toBe('https://192-168-1-20.abcdef.plex.direct:32400/library/parts/5678/1700000000/file.jpg?X-Plex-Token=TOKEN123')
  })

  it('appends download=1 when downloading', () => {
    const url = buildDownloadUrl(server, '/library/parts/5678/1700000000/file.jpg')
    expect(url).toContain('download=1')
    expect(url).toContain('X-Plex-Token=TOKEN123')
  })
})

describe('buildDirectPlayUrl', () => {
  it('matches the original file URL shape', () => {
    expect(buildDirectPlayUrl(server, '/p')).toBe(buildOriginalUrl(server, '/p', false))
  })
})

describe('buildHlsTranscodeUrl', () => {
  it('builds a start.m3u8 URL with all required params', () => {
    const url = buildHlsTranscodeUrl(server, {
      ratingKey: '1234',
      session: 'sess-1',
      clientIdentifier: 'client-abc',
    })
    expect(url).toContain('/video/:/transcode/universal/start.m3u8?')
    expect(url).toContain('path=%2Flibrary%2Fmetadata%2F1234')
    expect(url).toContain('protocol=hls')
    expect(url).toContain('directPlay=0')
    expect(url).toContain('directStream=1')
    expect(url).toContain('session=sess-1')
    expect(url).toContain('X-Plex-Client-Identifier=client-abc')
    expect(url).toContain('X-Plex-Token=TOKEN123')
  })
})

describe('buildHlsTranscodeStopUrl', () => {
  it('builds the stop URL', () => {
    const url = buildHlsTranscodeStopUrl(server, 'sess-1')
    expect(url).toBe('https://192-168-1-20.abcdef.plex.direct:32400/video/:/transcode/universal/stop?session=sess-1&X-Plex-Token=TOKEN123')
  })
})

describe('buildPlexWebLink', () => {
  it('builds the app.plex.tv deep link', () => {
    const url = buildPlexWebLink('machine-1', '1234')
    expect(url).toBe('https://app.plex.tv/desktop/#!/server/machine-1/details?key=%2Flibrary%2Fmetadata%2F1234')
  })
})
