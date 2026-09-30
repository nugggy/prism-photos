package au.prism.photos

import android.app.Application
import au.prism.photos.data.AppGraph
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

class PrismApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        au.prism.photos.data.CrashLog.install(this)
        graph = AppGraph(this)
    }

    /** Coil asks the Application for its singleton loader; hand back the shared one from the graph. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = graph.imageLoader

    companion object {
        lateinit var instance: PrismApp
            private set
        val graph: AppGraph get() = instance.graph
    }
}
