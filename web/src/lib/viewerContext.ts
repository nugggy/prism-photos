import { useOutletContext } from 'react-router-dom'
import type { MediaItem } from '../plex/model'

/**
 * What a list page (Photos, an album, Favourites, Search, Locked) hands to the viewer, which is
 * rendered as a nested route over the list so the list keeps its scroll position and state.
 */
export interface ViewerContext {
  /** The list the viewer steps through, in display order. */
  items: MediaItem[]
  /** Route of the list page, e.g. /photos or /albums/123. The viewer lives at `${basePath}/view/:itemId`. */
  basePath: string
  /** Lets the list reflect edits made in the viewer (favourite, rename). */
  onItemChanged?: (id: string, patch: Partial<MediaItem>) => void
  /** Lets the list drop items deleted from the viewer. */
  onItemsRemoved?: (ids: string[]) => void
}

export function viewerPath(basePath: string, itemId: string): string {
  return `${basePath}/view/${encodeURIComponent(itemId)}`
}

/** Absolute link to an item in this app, for share sheets. */
export function appItemLink(basePath: string, itemId: string): string {
  return `${window.location.origin}${window.location.pathname}#${viewerPath(basePath, itemId)}`
}

/** The list context when the viewer is nested under a list page, or undefined when standalone. */
export function useViewerContext(): ViewerContext | undefined {
  return useOutletContext<ViewerContext | undefined>() ?? undefined
}
