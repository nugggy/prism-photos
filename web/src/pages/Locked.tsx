import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLockStore } from '../state/lockStore'
import { useServer } from '../lib/useServer'
import { useSettingsStore } from '../state/settingsStore'
import { useSelection } from '../lib/useSelection'
import { useViewerListStore } from '../state/viewerListStore'
import { useTimeline } from '../lib/useTimeline'
import { PasscodeGate } from '../components/PasscodeGate'
import { MediaGrid } from '../components/MediaGrid'
import type { MediaItem } from '../plex/model'

export function Locked(): React.ReactElement {
  const unlocked = useLockStore((s) => s.unlocked)
  const lockedItemIds = useLockStore((s) => s.lockedItemIds)
  const lock = useLockStore((s) => s.lock)
  const { items } = useTimeline()
  const server = useServer()
  const density = useSettingsStore((s) => s.density)
  const setDensity = useSettingsStore((s) => s.setDensity)
  const setViewerList = useViewerListStore((s) => s.setList)
  const navigate = useNavigate()

  const lockedItems = useMemo(() => items.filter((i) => lockedItemIds.includes(i.id)), [items, lockedItemIds])
  const selection = useSelection(lockedItems)

  if (!unlocked) return <PasscodeGate />
  if (!server) return <div className="page">Not connected.</div>

  const openViewer = (_item: MediaItem, index: number) => {
    setViewerList('locked', lockedItems)
    navigate(`/view/locked/${index}`)
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Locked</h1>
        <button className="btn" onClick={lock}>
          Lock now
        </button>
      </div>
      {lockedItems.length === 0 && <p className="muted">Nothing locked yet. Lock items from Photos, Albums or the viewer.</p>}
      {lockedItems.length > 0 && (
        <MediaGrid
          items={lockedItems}
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
