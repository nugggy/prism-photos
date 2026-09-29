import { describe, expect, it } from 'vitest'
import type { PlexMetadataDto } from './dto'
import { mapMetadataToAlbum, mapMetadataToMediaItem, parseOriginallyAvailableAt } from './model'

// Shape taken verbatim from docs/plex-api.md "Listing photos and videos (timeline)".
const photoDto: PlexMetadataDto = {
  ratingKey: '1234',
  key: '/library/metadata/1234',
  guid: 'plex://photo/1234',
  type: 'photo',
  title: 'IMG_0001.jpg',
  summary: '',
  index: 1,
  year: 2024,
  thumb: '/library/metadata/1234/thumb/1700000000',
  originallyAvailableAt: '2024-03-14',
  addedAt: 1710403200,
  updatedAt: 1710403200,
  userRating: 10.0,
  createdAtAccuracy: 'local',
  createdAtTZOffset: '36000',
  parentRatingKey: '1200',
  parentKey: '/library/metadata/1200',
  parentTitle: '2024-03',
  Media: [
    {
      id: 5678,
      width: 4032,
      height: 3024,
      aspectRatio: 1.33,
      container: 'jpeg',
      aperture: 'f/1.8',
      exposure: '1/120',
      iso: 50,
      lens: '23mm',
      make: 'OnePlus',
      model: 'OnePlus 15',
      Part: [
        {
          id: 5678,
          key: '/library/parts/5678/1700000000/file.jpg',
          file: '/media/photos/2024/IMG_0001.jpg',
          size: 3456789,
          container: 'jpeg',
        },
      ],
    },
  ],
  Tag: [{ tag: 'Beach' }],
  Country: [{ tag: 'Australia' }],
  Place: [{ tag: 'Port Macquarie' }],
}

const clipDto: PlexMetadataDto = {
  ratingKey: '9999',
  key: '/library/metadata/9999',
  type: 'clip',
  title: 'VID_0001.mp4',
  addedAt: 1710403200,
  duration: 12345,
  Media: [
    {
      id: 111,
      width: 1920,
      height: 1080,
      videoCodec: 'hevc',
      audioCodec: 'aac',
      duration: 12345,
      videoResolution: '4k',
      Part: [{ id: 111, key: '/library/parts/111/1700000000/file.mp4', file: '/media/photos/2024/VID_0001.mp4', size: 999, duration: 12345 }],
    },
  ],
}

describe('parseOriginallyAvailableAt', () => {
  it('parses a YYYY-MM-DD date as local midnight', () => {
    const ms = parseOriginallyAvailableAt('2024-03-14')
    expect(ms).not.toBeNull()
    const d = new Date(ms!)
    expect(d.getFullYear()).toBe(2024)
    expect(d.getMonth()).toBe(2) // March
    expect(d.getDate()).toBe(14)
    expect(d.getHours()).toBe(0)
  })

  it('returns null when absent or malformed', () => {
    expect(parseOriginallyAvailableAt(undefined)).toBeNull()
    expect(parseOriginallyAvailableAt('not-a-date')).toBeNull()
  })
})

describe('mapMetadataToMediaItem', () => {
  it('maps a photo item per the documented field mapping', () => {
    const item = mapMetadataToMediaItem(photoDto, '3')
    expect(item.id).toBe('1234')
    expect(item.kind).toBe('photo')
    expect(item.width).toBe(4032)
    expect(item.height).toBe(3024)
    expect(item.partKey).toBe('/library/parts/5678/1700000000/file.jpg')
    expect(item.thumbPath).toBe('/library/metadata/1234/thumb/1700000000')
    expect(item.favourite).toBe(true)
    expect(item.albumId).toBe('1200')
    expect(item.fileSize).toBe(3456789)
    expect(item.filePath).toBe('/media/photos/2024/IMG_0001.jpg')
    expect(item.tags).toEqual(['Beach'])
    expect(item.place).toBe('Port Macquarie')
    expect(item.country).toBe('Australia')
    expect(item.exif.aperture).toBe('f/1.8')
    expect(item.exif.iso).toBe(50)
    expect(item.sectionKey).toBe('3')
  })

  it('maps a clip (video) item, using type=clip as isVideo and duration in ms', () => {
    const item = mapMetadataToMediaItem(clipDto, '3')
    expect(item.kind).toBe('video')
    expect(item.durationMs).toBe(12345)
    expect(item.favourite).toBe(false)
    expect(item.exif.videoCodec).toBe('hevc')
    // No originallyAvailableAt -> falls back to addedAt (seconds -> ms)
    expect(item.takenAt).toBe(1710403200 * 1000)
  })
})

describe('mapMetadataToAlbum', () => {
  it('prefers composite over thumb for the cover, falls back to thumb', () => {
    const withComposite = mapMetadataToAlbum(
      { ratingKey: '1200', key: '/library/metadata/1200/children', type: 'photoalbum', title: '2024-03', composite: '/composite.jpg', thumb: '/thumb.jpg', leafCount: 42, addedAt: 1700000000 },
      '3',
      null,
    )
    expect(withComposite.thumbPath).toBe('/composite.jpg')
    expect(withComposite.itemCount).toBe(42)

    const thumbOnly = mapMetadataToAlbum(
      { ratingKey: '1201', key: '/library/metadata/1201/children', type: 'photoalbum', title: 'Trip', thumb: '/thumb.jpg' },
      '3',
      '1200',
    )
    expect(thumbOnly.thumbPath).toBe('/thumb.jpg')
    expect(thumbOnly.parentId).toBe('1200')
  })
})
