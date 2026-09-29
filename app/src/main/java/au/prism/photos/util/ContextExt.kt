package au.prism.photos.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Walks the context wrapper chain to find the hosting Activity, or null if there isn't one. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
