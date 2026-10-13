package au.prism.photos.data.upload

/** What sits at a remote path. [Unknown] means the check itself failed (timeout, auth, odd reply). */
sealed interface RemoteState {
    data object Absent : RemoteState

    /** Something exists. [size] is -1 when it is a folder or the size could not be read. */
    data class Present(val size: Long) : RemoteState

    data class Unknown(val reason: String) : RemoteState
}

/** Outcome of [ClashNames.resolve]. */
sealed interface ClashResolution {
    /**
     * Write to [name]. [replaces] is true when a file of exactly the same size already sits there
     * (almost certainly an earlier upload of this file), so overwriting it is expected; false
     * means the name is free and the write must refuse to replace anything that appears meanwhile.
     */
    data class Use(val name: String, val replaces: Boolean) : ClashResolution

    /** The destination could not be checked, so nothing is written; the file is retried next time. */
    data class CannotCheck(val reason: String) : ClashResolution
}

/**
 * Pure clash handling for uploads, free of Android and network code so it can be unit tested.
 * Appends _1, _2... to the file name while a different file already sits at that path.
 */
object ClashNames {
    private const val MAX_SUFFIX = 50

    suspend fun resolve(
        subFolder: String,
        displayName: String,
        size: Long,
        probe: suspend (path: String) -> RemoteState,
    ): ClashResolution {
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val ext = if (dot > 0) displayName.substring(dot) else ""
        var candidate = displayName
        var suffix = 1
        while (true) {
            val path = if (subFolder.isBlank()) candidate else "$subFolder/$candidate"
            when (val state = probe(path)) {
                RemoteState.Absent -> return ClashResolution.Use(candidate, replaces = false)
                is RemoteState.Present -> if (size > 0 && state.size == size) return ClashResolution.Use(candidate, replaces = true)
                is RemoteState.Unknown -> return ClashResolution.CannotCheck(state.reason)
            }
            if (suffix > MAX_SUFFIX) return ClashResolution.CannotCheck("more than $MAX_SUFFIX files already use the name $displayName")
            candidate = "${base}_$suffix$ext"
            suffix++
        }
    }

    /** Hidden temporary name in the same folder that an upload is written to before being renamed into place. */
    fun tempPathFor(path: String): String {
        val slash = path.lastIndexOf('/')
        val folder = if (slash >= 0) path.substring(0, slash + 1) else ""
        val name = path.substring(slash + 1)
        return "$folder.$name.part"
    }
}
