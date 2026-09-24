package com.splitmypay.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitmypay.app.data.local.entity.MemberEntity
import com.splitmypay.app.data.local.entity.TricountEntity
import com.splitmypay.app.ui.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagementScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val tricounts by viewModel.tricounts.collectAsState()
    val defaultTricount by viewModel.defaultTricount.collectAsState()

    var inputLink by remember { mutableStateOf("") }
    var detectedClipboardLink by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // Automatic clipboard detection on entry
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
            if (text.contains("tricount.com/") || text.matches(Regex("""[a-zA-Z0-9_-]{10,30}"""))) {
                detectedClipboardLink = text
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tricount Groups", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Detected Clipboard Prompt
            detectedClipboardLink?.let { clipUrl ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Detected Tricount Link in Clipboard",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = clipUrl,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(onClick = { detectedClipboardLink = null }) {
                                    Text("Dismiss")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        inputLink = clipUrl
                                        detectedClipboardLink = null
                                    }
                                ) {
                                    Text("Use Link")
                                }
                            }
                        }
                    }
                }
            }

            // Import Box
            item {
                Text(
                    text = "Import New Group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputLink,
                    onValueChange = { inputLink = it },
                    label = { Text("Tricount URL or Token") },
                    placeholder = { Text("https://tricount.com/t...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (inputLink.isBlank()) {
                            Toast.makeText(context, "Please enter a valid Tricount link", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isImporting = true
                        coroutineScope.launch {
                            val res = viewModel.importTricount(inputLink)
                            isImporting = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Group \"${res.getOrNull()?.title}\" imported!", Toast.LENGTH_SHORT).show()
                                inputLink = ""
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "Unknown error"
                                Toast.makeText(context, "Import failed: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isImporting && inputLink.isNotBlank()
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text("Import Group")
                    }
                }
            }

            // Synced Groups Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connected Groups (${tricounts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (tricounts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No groups imported yet. Paste your Tricount link above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(tricounts) { tricount ->
                    val isDefault = tricount.id == defaultTricount?.id
                    val members by viewModel.getMembersForTricount(tricount.id).collectAsState(initial = emptyList())

                    GroupCardDetail(
                        tricount = tricount,
                        members = members,
                        isDefault = isDefault,
                        onSetDefault = { viewModel.setDefaultTricount(tricount.id) },
                        onSetCurrentUser = { memberUuid ->
                            viewModel.setCurrentUser(tricount.id, memberUuid)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun GroupCardDetail(
    tricount: TricountEntity,
    members: List<MemberEntity>,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onSetCurrentUser: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tricount.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Token: ${tricount.publicToken} • ${tricount.currency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://tricount.com/${tricount.publicToken}")
                            ).apply {
                                setPackage("com.tribab.tricount.android")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://tricount.com/${tricount.publicToken}")
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in Tricount App",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onSetDefault) {
                        Icon(
                            imageVector = if (isDefault) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Default",
                            tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Members (Select \"Which member is me\" for default payer):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                members.forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (m.isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = m.displayName,
                                fontWeight = if (m.isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                        FilterChip(
                            selected = m.isCurrentUser,
                            onClick = { onSetCurrentUser(m.uuid) },
                            label = { Text(if (m.isCurrentUser) "Me ✓" else "Set as Me") }
                        )
                    }
                }
            }
        }
    }
}
