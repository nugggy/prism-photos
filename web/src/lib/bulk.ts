import { useLockStore } from '../state/lockStore'
import { errorMessage, showToast } from '../state/toastStore'
import type { MediaItem } from '../plex/model'
import type { ServerRef } from '../plex/urls'
import { buildDownloadUrl } from '../plex/urls'
import { downloadMany } from './download'

export interface BulkResult {
  succeeded: string[]
  failed: string[]
  firstError: unknown
}

/** Runs an action for each id, collecting which ones worked instead of stopping at the first failure. */
export async function runForEach(ids: string[], action: (id: string) => Promise<void>): Promise<BulkResult> {
  const results = await Promise.allSettled(ids.map((id) => action(id)))
  const succeeded: string[] = []
  const failed: string[] = []
  let firstError: unknown = null
  results.forEach((r, i) => {
    if (r.status === 'fulfilled') succeeded.push(ids[i])
    else {
      failed.push(ids[i])
      firstError ??= r.reason
    }
  })
  return { succeeded, failed, firstError }
}

/** Shows an error toast describing a bulk action that failed for some or all items. */
export function reportBulkFailure(result: BulkResult, verb: string): void {
  if (result.failed.length === 0) return
  const total = result.succeeded.length + result.failed.length
  const reason = errorMessage(result.firstError, 'The server did not accept the change.')
  const summary = result.succeeded.length === 0 ? `Could not ${verb} ${total === 1 ? 'the item' : `the ${total} items`}.` : `Could not ${verb} ${result.failed.length} of ${total} items.`
  showToast(`${summary} ${reason}`)
}

/** Locks items, or explains that a passcode is needed first. Returns whether they were locked. */
export function lockItemsOrExplain(ids: string[]): boolean {
  const ok = useLockStore.getState().lockItems(ids)
  if (!ok) showToast('Set a passcode on the Locked page before locking items.', 'info')
  return ok
}

/** Downloads items one after another, since browsers block a burst of simultaneous downloads. */
export function downloadItems(server: ServerRef, items: MediaItem[]): void {
  if (items.length > 3) {
    showToast(`Downloading ${items.length} files one at a time. If your browser asks, allow multiple downloads for this site.`, 'info')
  }
  void downloadMany(items.map((i) => ({ url: buildDownloadUrl(server, i.partKey), filename: i.title })))
}
