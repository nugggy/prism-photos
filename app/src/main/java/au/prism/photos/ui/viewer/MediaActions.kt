package au.prism.photos.ui.viewer

import android.content.Context
import au.prism.photos.domain.MediaItem

/**
 * STUB. Replaced by the viewer agent. Shared item actions used by the viewer and by the
 * multi-select bar in the grids. Each is a suspend function that reports success or failure.
 */
object MediaActions {
    /** Saves the original file to the device Downloads (Plex items) or copies device items. */
    suspend fun download(context: Context, items: List<MediaItem>): Result<Int> = Result.success(0)

    /** Sets the item as the home or lock screen wallpaper. */
    suspend fun setWallpaper(context: Context, item: MediaItem, lockScreen: Boolean): Result<Unit> = Result.success(Unit)
}
