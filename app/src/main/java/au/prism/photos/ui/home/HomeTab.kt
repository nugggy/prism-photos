package au.prism.photos.ui.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.ui.graphics.vector.ImageVector
import au.prism.photos.R

enum class HomeTab(@StringRes val label: Int, val outlined: ImageVector, val filled: ImageVector) {
    PHOTOS(R.string.home_tab_photos, Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    ALBUMS(R.string.home_tab_albums, Icons.Outlined.PhotoAlbum, Icons.Filled.PhotoAlbum),
    FAVOURITES(R.string.home_tab_favourites, Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    DEVICE(R.string.home_tab_device, Icons.Outlined.PhoneAndroid, Icons.Filled.PhoneAndroid),
    LOCKED(R.string.home_tab_locked, Icons.Outlined.Lock, Icons.Filled.Lock),
}
