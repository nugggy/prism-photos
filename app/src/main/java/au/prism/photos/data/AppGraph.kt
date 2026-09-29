package au.prism.photos.data

import android.app.Application
import au.prism.photos.data.fake.FakeDeviceMediaSource
import au.prism.photos.data.fake.FakeLocalStore
import au.prism.photos.data.fake.FakeMediaRepository
import au.prism.photos.data.fake.FakePlexAuth
import au.prism.photos.data.fake.FakeSessionStore
import au.prism.photos.data.fake.FakeSettingsStore
import au.prism.photos.data.fake.FakeUpdateChecker
import au.prism.photos.domain.DeviceMediaSource
import au.prism.photos.domain.LocalStore
import au.prism.photos.domain.MediaRepository
import au.prism.photos.domain.PlexAuth
import au.prism.photos.domain.SessionStore
import au.prism.photos.domain.SettingsStore
import au.prism.photos.domain.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph. The data layer owns this file and replaces the fakes
 * with real implementations. UI code reaches it through PrismApp.graph.
 */
class AppGraph(val app: Application) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsStore = FakeSettingsStore()
    val session: SessionStore = FakeSessionStore()
    val local: LocalStore = FakeLocalStore()
    val auth: PlexAuth = FakePlexAuth()
    val media: MediaRepository = FakeMediaRepository(local, scope)
    val updates: UpdateChecker = FakeUpdateChecker()
    val device: DeviceMediaSource = FakeDeviceMediaSource()
}
