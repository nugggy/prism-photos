import type { MediaItem } from './model'

/** Merges photo (type=13) and clip (type=12) pages into one newest-first timeline. */
export function mergeTimeline(photos: MediaItem[], clips: MediaItem[]): MediaItem[] {
  return [...photos, ...clips].sort((a, b) => {
    if (b.takenAt !== a.takenAt) return b.takenAt - a.takenAt
    return b.addedAt - a.addedAt
  })
}

export interface MonthGroup {
  key: string // yyyy-MM
  label: string // e.g. "March 2024"
  items: MediaItem[]
}

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

/** Groups an already-sorted (newest first) timeline into month buckets with sticky headers. */
export function groupByMonth(items: MediaItem[]): MonthGroup[] {
  const groups: MonthGroup[] = []
  let current: MonthGroup | null = null
  for (const item of items) {
    const d = new Date(item.takenAt)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    if (!current || current.key !== key) {
      current = { key, label: `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}`, items: [] }
      groups.push(current)
    }
    current.items.push(item)
  }
  return groups
}

/** Removes items whose id is in the locked set, used to hide locked items everywhere else. */
export function filterLocked(items: MediaItem[], lockedIds: ReadonlySet<string>): MediaItem[] {
  if (lockedIds.size === 0) return items
  return items.filter((item) => !lockedIds.has(item.id))
}

/** De-duplicates by id, keeping the first occurrence (used when merging paged results). */
export function dedupeById(items: MediaItem[]): MediaItem[] {
  const seen = new Set<string>()
  const result: MediaItem[] = []
  for (const item of items) {
    if (seen.has(item.id)) continue
    seen.add(item.id)
    result.push(item)
  }
  return result
}
