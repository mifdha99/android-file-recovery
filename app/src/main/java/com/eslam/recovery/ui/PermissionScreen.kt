package com.eslam.recovery.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eslam.recovery.data.permission.Permissions

@Composable
fun PermissionScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var hasMedia by remember { mutableStateOf(Permissions.hasMedia(context)) }

    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshAccess() }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshAccess() }
    val media = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasMedia = Permissions.hasMedia(context)
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("File Recovery", style = MaterialTheme.typography.headlineMedium)
            Text(
                "To find deleted photos, videos and files the app must read the whole storage, including the " +
                    "system trash and recycle bins of other apps. Nothing leaves your phone and original files are never changed.",
                style = MaterialTheme.typography.bodyMedium,
            )

            PermissionCard(
                icon = Icons.Default.FolderOpen,
                title = "All files access",
                text = "Required. Full file management for files, images and videos.",
                granted = false,
                button = "Allow",
            ) {
                if (Permissions.needsSettingsScreen) {
                    try {
                        settings.launch(Permissions.allFilesAccessIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        settings.launch(Permissions.allFilesAccessFallbackIntent())
                    }
                } else {
                    legacy.launch(Permissions.legacyStorage)
                }
            }

            if (Permissions.needsSettingsScreen) {
                PermissionCard(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Photos and videos",
                    text = "Optional. Keeps location data inside recovered photos.",
                    granted = hasMedia,
                    button = "Allow",
                ) { media.launch(Permissions.media) }
            }
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, text: String, granted: Boolean, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            if (granted) Icon(Icons.Default.CheckCircle, "Granted", tint = MaterialTheme.colorScheme.primary)
            else Button(onClick) { Text(button) }
        }
    }
}
