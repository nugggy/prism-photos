package au.prism.photos.ui.servers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import au.prism.photos.PrismApp
import au.prism.photos.domain.PlexLibrary
import au.prism.photos.ui.components.ErrorState
import au.prism.photos.ui.components.LoadingState
import au.prism.photos.ui.theme.PlexGold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPickerScreen(onSelected: () -> Unit) {
    val graph = PrismApp.graph
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var libraries by remember { mutableStateOf<List<PlexLibrary>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        graph.media.libraries()
            .onSuccess { libraries = it; loading = false }
            .onFailure { error = it.message ?: "Could not load libraries"; loading = false }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Choose a library") }) }) { padding ->
        when {
            loading -> LoadingState(Modifier.padding(padding))
            error != null -> ErrorState(error!!, onRetry = {
                loading = true
                error = null
                scope.launch {
                    graph.media.libraries()
                        .onSuccess { libraries = it; loading = false }
                        .onFailure { error = it.message ?: "Could not load libraries"; loading = false }
                }
            }, modifier = Modifier.padding(padding))
            libraries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No photo libraries on this server")
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                items(libraries, key = { it.key }) { library ->
                    Card(
                        onClick = {
                            scope.launch {
                                graph.session.update { it.copy(libraryKey = library.key, libraryTitle = library.title) }
                                graph.scope.launch { graph.media.refreshTimeline(force = true) }
                                onSelected()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    ) {
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = PlexGold) },
                            headlineContent = { Text(library.title) },
                        )
                    }
                }
            }
        }
    }
}
