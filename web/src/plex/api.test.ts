import { describe, expect, it } from 'vitest'
import type { PlexMetadataDto } from './dto'
import { buildPlaylistUri, mapRatingKeysToPlaylistItemIds } from './api'

describe('buildPlaylistUri', () => {
  it('builds a server metadata uri for a single item', () => {
    expect(buildPlaylistUri('machine-1', ['1'])).toBe(
      'server://machine-1/com.plexapp.plugins.library/library/metadata/1',
    )
  })

  it('joins several item ids with commas', () => {
    expect(buildPlaylistUri('machine-1', ['1', '2', '3'])).toBe(
      'server://machine-1/com.plexapp.plugins.library/library/metadata/1,2,3',
    )
  })

  it('is percent encoded when used as a query parameter value', () => {
    const uri = buildPlaylistUri('machine-1', ['1', '2'])
    const params = new URLSearchParams({ uri })
    expect(params.toString()).toBe(
      'uri=server%3A%2F%2Fmachine-1%2Fcom.plexapp.plugins.library%2Flibrary%2Fmetadata%2F1%2C2',
    )
  })
})

describe('mapRatingKeysToPlaylistItemIds', () => {
  const items: PlexMetadataDto[] = [
    { ratingKey: '1', key: '/library/metadata/1', type: 'photo', title: 'A', playlistItemID: 101 },
    { ratingKey: '2', key: '/library/metadata/2', type: 'photo', title: 'B', playlistItemID: 102 },
    { ratingKey: '3', key: '/library/metadata/3', type: 'photo', title: 'C', playlistItemID: 103 },
  ]

  it('maps a single requested rating key to its playlistItemID', () => {
    expect(mapRatingKeysToPlaylistItemIds(items, ['2'])).toEqual([102])
  })

  it('maps several rating keys in the order they appear in the playlist, not the request order', () => {
    expect(mapRatingKeysToPlaylistItemIds(items, ['3', '1'])).toEqual([101, 103])
  })

  it('ignores rating keys that are not present or have no playlistItemID', () => {
    const withMissing: PlexMetadataDto[] = [
      ...items,
      { ratingKey: '4', key: '/library/metadata/4', type: 'photo', title: 'D' },
    ]
    expect(mapRatingKeysToPlaylistItemIds(withMissing, ['4', '5', '1'])).toEqual([101])
  })

  it('returns an empty array when nothing matches', () => {
    expect(mapRatingKeysToPlaylistItemIds(items, ['99'])).toEqual([])
  })
})
