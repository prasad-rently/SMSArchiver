package com.prasad.smsarchiver.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.prasad.smsarchiver.R
import com.prasad.smsarchiver.data.model.SmsMessage
import com.prasad.smsarchiver.ui.theme.SMSArchiverTheme
import com.prasad.smsarchiver.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "📩 Received NEW_SMS broadcast, refreshing UI...")
            // Auto-refresh messages when new SMS arrives
            viewModel.loadMessages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register receiver for new SMS notifications
        val filter = IntentFilter("com.prasad.smsarchiver.NEW_SMS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }
        Log.d("MainActivity", "Registered NEW_SMS receiver")
        
        setContent {
            SMSArchiverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Request necessary permissions
    val permissionsToRequest = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.loadMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (!permissionsState.allPermissionsGranted) {
                PermissionRequestContent(
                    onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                )
            } else {
                MainContent(
                    uiState = uiState,
                    onStartMonitoring = { viewModel.startMonitoring() },
                    onStopMonitoring = { viewModel.stopMonitoring() },
                    onRefresh = { viewModel.loadMessages() }
                )
            }

            // Show error snackbar
            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    // Error handling could be enhanced with Snackbar
                    viewModel.clearError()
                }
            }
        }
    }
}

@Composable
fun PermissionRequestContent(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.permission_required),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Button(onClick = onRequestPermissions) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
fun MainContent(
    uiState: com.prasad.smsarchiver.ui.viewmodel.MainUiState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(
                        R.string.sms_count,
                        uiState.messageCount
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                
                uiState.lastSyncTime?.let { time ->
                    Text(
                        text = stringResource(R.string.last_sync, time),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Text(
                    text = stringResource(
                        R.string.sync_status,
                        if (uiState.isMonitoring) 
                            stringResource(R.string.monitoring_active)
                        else 
                            stringResource(R.string.monitoring_inactive)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isMonitoring) 
                        MaterialTheme.colorScheme.primary
                    else 
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Control Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = if (uiState.isMonitoring) onStopMonitoring else onStartMonitoring,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (uiState.isMonitoring) 
                        stringResource(R.string.stop_monitoring)
                    else 
                        stringResource(R.string.start_monitoring)
                )
            }
            
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
                enabled = !uiState.isLoading
            ) {
                Text("Refresh")
            }
        }

        // Messages List
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages.take(50)) { message ->
                    SmsMessageItem(message)
                }
                
                if (uiState.messages.size > 50) {
                    item {
                        Text(
                            text = "Showing 50 of ${uiState.messages.size} messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmsMessageItem(message: SmsMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.address,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 3
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(smsReceiver)
            Log.d("MainActivity", "Unregistered NEW_SMS receiver")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
    }
}
