package au.prism.photos

import android.app.Application
import au.prism.photos.data.AppGraph

class PrismApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
    }

    companion object {
        lateinit var instance: PrismApp
            private set
        val graph: AppGraph get() = instance.graph
    }
}
