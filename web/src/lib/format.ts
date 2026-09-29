// Australian date/number formatting helpers.

const dateFormatter = new Intl.DateTimeFormat('en-AU', { day: '2-digit', month: 'long', year: 'numeric' })
const dateTimeFormatter = new Intl.DateTimeFormat('en-AU', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})
const shortDateFormatter = new Intl.DateTimeFormat('en-AU', { day: '2-digit', month: '2-digit', year: 'numeric' })

export function formatDate(ms: number): string {
  return dateFormatter.format(new Date(ms))
}

export function formatDateTime(ms: number): string {
  return dateTimeFormatter.format(new Date(ms))
}

export function formatShortDate(ms: number): string {
  return shortDateFormatter.format(new Date(ms))
}

export function formatFileSize(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(value < 10 && unitIndex > 0 ? 1 : 0)} ${units[unitIndex]}`
}

export function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

/** Filename timestamp used for editor exports, e.g. Prism_20260929_143022.jpg */
export function exportFileName(prefix = 'PlexGallery'): string {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  const ss = String(now.getSeconds()).padStart(2, '0')
  return `${prefix}_${y}${m}${d}_${hh}${mm}${ss}.jpg`
}
