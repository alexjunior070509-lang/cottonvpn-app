package io.nekohasekai.sfa.compose.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status

/**
 * Минимальный главный экран CottonVPN: поле для ключа + одна большая кнопка «Подключиться».
 * Вся VPN-механика приходит через колбэки из MainActivity (startService / BoxService.stop).
 */
@Composable
fun CottonHomeScreen(
    serviceStatus: Status,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    pendingKeyUrl: String?,
    onPendingKeyConsumed: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }

    // Ключ из deeplink (бот-ссылка /app/{token}) — сохраняем автоматически.
    LaunchedEffect(pendingKeyUrl) {
        if (pendingKeyUrl != null) {
            viewModel.saveKey(pendingKeyUrl)
            onPendingKeyConsumed()
        }
    }
    LaunchedEffect(ui.savedOk) {
        if (ui.savedOk) {
            editing = false
            keyInput = ""
            viewModel.clearSavedOk()
        }
    }
    LaunchedEffect(Unit) { viewModel.refresh() }

    val running = serviceStatus == Status.Started || serviceStatus == Status.Starting
    val transitioning = serviceStatus == Status.Starting || serviceStatus == Status.Stopping
    val showKeyField = !ui.hasKey || editing

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).statusBarsPadding(),
        ) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.title_settings))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "CottonVPN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(statusTextRes(serviceStatus)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))

            val buttonColor = when {
                running -> MaterialTheme.colorScheme.errorContainer
                ui.hasKey -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Surface(
                onClick = {
                    when (serviceStatus) {
                        Status.Stopped -> if (ui.hasKey) onConnect() else editing = true
                        Status.Started, Status.Starting -> onDisconnect()
                        Status.Stopping -> {}
                    }
                },
                enabled = !transitioning,
                shape = CircleShape,
                color = buttonColor,
                modifier = Modifier.size(180.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (transitioning) {
                        CircularProgressIndicator()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    if (running) R.string.cotton_disconnect else R.string.cotton_connect,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            if (showKeyField) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(stringResource(R.string.cotton_key_hint)) },
                    singleLine = true,
                    enabled = !ui.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.saveKey(keyInput) },
                    enabled = keyInput.isNotBlank() && !ui.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (ui.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.cotton_save_key))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.cotton_key_saved),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        editing = true
                        keyInput = ""
                    }) {
                        Text(stringResource(R.string.cotton_change_key))
                    }
                }
            }

            ui.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun statusTextRes(status: Status): Int = when (status) {
    Status.Stopped -> R.string.status_default
    Status.Starting -> R.string.status_starting
    Status.Started -> R.string.status_started
    Status.Stopping -> R.string.status_stopping
}
