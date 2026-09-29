import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useServer } from '../lib/useServer'
import { useSessionStore } from '../state/sessionStore'
import { useLockStore } from '../state/lockStore'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useViewerListStore } from '../state/viewerListStore'
import { searchLibrary } from '../plex/api'
import type { MediaItem } from '../plex/model'
import { filterLocked } from '../plex/timeline'
import { MediaGrid } from '../components/MediaGrid'

const CURRENT_YEAR = new Date().getFullYear()
const YEARS = Array.from({ length: 8 }, (_, i) => CURRENT_YEAR - i)

export function Search(): React.ReactElement {
  const server = useServer()
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const setViewerList = useViewerListStore((s) => s.setList)
  const navigate = useNavigate()

  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [tag, setTag] = useState<string | null>(null)
  const [year, setYear] = useState<number | null>(null)
  const [items, setItems] = useState<MediaItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 350)
    return () => clearTimeout(t)
  }, [query])

  useEffect(() => {
    if (!server || !sectionKey) return
    if (!debounced && !tag && !year) {
      setItems([])
      return
    }
    let cancelled = false
    setLoading(true)
    searchLibrary(server, sectionKey, { query: debounced || undefined, tag: tag ?? undefined, year: year ?? undefined })
      .then((data) => !cancelled && setItems(data))
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Search failed.'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [server, sectionKey, debounced, tag, year])

  const tagOptions = useMemo(() => {
    const set = new Set<string>()
    items.forEach((i) => i.tags.forEach((t) => set.add(t)))
    return Array.from(set).slice(0, 12)
  }, [items])

  const visibleItems = useMemo(() => filterLocked(items, new Set(lockedItemIds)), [items, lockedItemIds])
  const selection = useSelection(visibleItems)

  if (!server) return <div className="page">Not connected.</div>

  const openViewer = (_item: MediaItem, index: number) => {
    setViewerList('search', visibleItems)
    navigate(`/view/search/${index}`)
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Search</h1>
      </div>
      <input className="input" placeholder="Search by title, tag, place or camera model" value={query} onChange={(e) => setQuery(e.target.value)} style={{ marginBottom: 12 }} />

      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 6 }}>
        {YEARS.map((y) => (
          <button key={y} className={`chip${year === y ? ' active' : ''}`} onClick={() => setYear(year === y ? null : y)}>
            {y}
          </button>
        ))}
      </div>
      {tagOptions.length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 16 }}>
          {tagOptions.map((t) => (
            <button key={t} className={`chip${tag === t ? ' active' : ''}`} onClick={() => setTag(tag === t ? null : t)}>
              {t}
            </button>
          ))}
        </div>
      )}

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {loading && <div className="spinner" />}
      {!loading && !debounced && !tag && !year && <p className="muted">Start typing to search your library.</p>}
      {!loading && (debounced || tag || year) && visibleItems.length === 0 && <p className="muted">No results.</p>}

      {visibleItems.length > 0 && (
        <MediaGrid
          items={visibleItems}
          server={server}
          density={density}
          onDensityChange={setDensity}
          selectedIds={selection.selectedIds}
          onToggleSelect={selection.toggle}
          onOpen={openViewer}
          selectionMode={selection.selectionMode}
        />
      )}
    </div>
  )
}
