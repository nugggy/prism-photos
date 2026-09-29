package au.prism.photos.ui.home

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

enum class HomeTab(val label: String, val outlined: ImageVector, val filled: ImageVector) {
    PHOTOS("Photos", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    ALBUMS("Albums", Icons.Outlined.PhotoAlbum, Icons.Filled.PhotoAlbum),
    FAVOURITES("Favourites", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    DEVICE("Device", Icons.Outlined.PhoneAndroid, Icons.Filled.PhoneAndroid),
    LOCKED("Locked", Icons.Outlined.Lock, Icons.Filled.Lock),
}
