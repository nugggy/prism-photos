package au.prism.photos.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.prism.photos.PrismApp
import au.prism.photos.R
import au.prism.photos.domain.ConnectionMode
import au.prism.photos.domain.PlexServer
import au.prism.photos.ui.components.ErrorState
import au.prism.photos.ui.components.LoadingState
import au.prism.photos.ui.theme.PlexGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPickerScreen(onSelected: () -> Unit) {
    val graph = PrismApp.graph
    val vm: ServerPickerViewModel = viewModel(factory = viewModelFactory { initializer { ServerPickerViewModel(graph) } })

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.servers_choose_server)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConnectionModeChip(stringResource(R.string.servers_mode_auto), vm.connectionMode == ConnectionMode.AUTO) { vm.changeConnectionMode(ConnectionMode.AUTO) }
                ConnectionModeChip(stringResource(R.string.servers_mode_lan), vm.connectionMode == ConnectionMode.LAN) { vm.changeConnectionMode(ConnectionMode.LAN) }
                ConnectionModeChip(stringResource(R.string.servers_mode_remote), vm.connectionMode == ConnectionMode.REMOTE) { vm.changeConnectionMode(ConnectionMode.REMOTE) }
            }
            when {
                vm.loading -> LoadingState()
                vm.error != null -> ErrorState(vm.error!!, onRetry = { vm.load() })
                vm.servers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.servers_none)) }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(vm.servers, key = { it.clientIdentifier }) { server ->
                        ServerRow(server, vm.statuses[server.clientIdentifier], onClick = { vm.select(server, onSelected) })
                        Spacer(Modifier.width(0.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ServerRow(server: PlexServer, status: ProbeStatus?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Dns, contentDescription = null, tint = PlexGold) },
            headlineContent = { Text(server.name) },
            supportingContent = {
                Column {
                    Text(server.platform.ifBlank { "Plex Media Server" }, style = MaterialTheme.typography.bodySmall)
                    when (status) {
                        is ProbeStatus.Ok -> Text(stringResource(R.string.servers_connected, status.active.kind.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        is ProbeStatus.Failed -> Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        ProbeStatus.Checking -> Text(stringResource(R.string.servers_checking), style = MaterialTheme.typography.bodySmall)
                        null -> {}
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (server.owned) {
                        SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.servers_owned)) }, colors = SuggestionChipDefaults.suggestionChipColors())
                        Spacer(Modifier.width(8.dp))
                    }
                    when (status) {
                        is ProbeStatus.Ok -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        is ProbeStatus.Failed -> Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        ProbeStatus.Checking -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        null -> {}
                    }
                }
            },
        )
    }
}
