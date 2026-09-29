import { describe, expect, it } from 'vitest'
import { dedupeById, filterLocked, groupByMonth, mergeTimeline } from './timeline'
import type { MediaItem } from './model'

function item(id: string, takenAt: number, addedAt = takenAt, overrides: Partial<MediaItem> = {}): MediaItem {
  return {
    id,
    kind: 'photo',
    title: id,
    summary: '',
    takenAt,
    addedAt,
    width: 100,
    height: 100,
    thumbPath: '',
    partKey: '',
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
    sectionKey: '3',
    ...overrides,
  }
}

describe('mergeTimeline', () => {
  it('merges photos and clips sorted newest first by takenAt', () => {
    const photos = [item('p1', 3000), item('p2', 1000)]
    const clips = [item('c1', 2000, 2000, { kind: 'video' })]
    const merged = mergeTimeline(photos, clips)
    expect(merged.map((i) => i.id)).toEqual(['p1', 'c1', 'p2'])
  })

  it('breaks ties on takenAt using addedAt', () => {
    const a = item('a', 1000, 500)
    const b = item('b', 1000, 900)
    const merged = mergeTimeline([a, b], [])
    expect(merged.map((i) => i.id)).toEqual(['b', 'a'])
  })
})

describe('dedupeById', () => {
  it('keeps only the first occurrence of each id', () => {
    const items = [item('a', 3000), item('a', 3000), item('b', 2000)]
    expect(dedupeById(items).map((i) => i.id)).toEqual(['a', 'b'])
  })
})

describe('groupByMonth', () => {
  it('groups a newest-first timeline into month buckets', () => {
    const items = [
      item('a', new Date(2024, 2, 20).getTime()),
      item('b', new Date(2024, 2, 10).getTime()),
      item('c', new Date(2024, 1, 15).getTime()),
    ]
    const groups = groupByMonth(items)
    expect(groups).toHaveLength(2)
    expect(groups[0].label).toBe('March 2024')
    expect(groups[0].items.map((i) => i.id)).toEqual(['a', 'b'])
    expect(groups[1].label).toBe('February 2024')
  })
})

describe('filterLocked', () => {
  it('removes items whose id is locked', () => {
    const items = [item('a', 1), item('b', 2)]
    const result = filterLocked(items, new Set(['a']))
    expect(result.map((i) => i.id)).toEqual(['b'])
  })

  it('returns the same items when nothing is locked', () => {
    const items = [item('a', 1)]
    expect(filterLocked(items, new Set())).toEqual(items)
  })
})
