package com.boostgo.customercare.ui

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen() {
    var phoneNumber by remember { mutableStateOf(TextFieldValue("")) }
    var message by remember { mutableStateOf(TextFieldValue("")) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    var smsStatus by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Permission launcher for SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sendSmsWithManager(context, phoneNumber.text, message.text) { status ->
                smsStatus = status
                snackbarMessage = status
                showSnackbar = true
            }
            // Clear fields after sending
            phoneNumber = TextFieldValue("")
            message = TextFieldValue("")
        } else {
            snackbarMessage = "SMS permission denied. Cannot send SMS."
            showSnackbar = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Send SMS",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Phone Number Input
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            placeholder = { Text("Enter phone number (e.g., +1234567890)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Message Input
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            placeholder = { Text("Enter your message here...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5
        )

        // Character count
        Text(
            text = "${message.text.length}/160 characters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End)
        )

        // SMS Status
        if (smsStatus.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (smsStatus.contains("delivered", ignoreCase = true))
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Status: $smsStatus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (smsStatus.contains("delivered", ignoreCase = true))
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Send Button
        Button(
            onClick = {
                if (phoneNumber.text.isBlank()) {
                    snackbarMessage = "Please enter a phone number"
                    showSnackbar = true
                } else if (message.text.isBlank()) {
                    snackbarMessage = "Please enter a message"
                    showSnackbar = true
                } else {
                    // Check SMS permission
                    when (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.SEND_SMS
                    )) {
                        PackageManager.PERMISSION_GRANTED -> {
                            sendSmsWithManager(context, phoneNumber.text, message.text) { status ->
                                smsStatus = status
                                snackbarMessage = status
                                showSnackbar = true
                            }
                            // Clear fields after sending
                            phoneNumber = TextFieldValue("")
                            message = TextFieldValue("")
                        }

                        else -> {
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = phoneNumber.text.isNotBlank() && message.text.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send SMS",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Send SMS", fontSize = 16.sp)
        }

        // Quick Actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            phoneNumber = TextFieldValue("+1234567890")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sample Number")
                    }

                    OutlinedButton(
                        onClick = {
                            message =
                                TextFieldValue("Hello! This is a test message from Customer Care app.")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sample Message")
                    }
                }
            }
        }
    }

    // Snackbar
    if (showSnackbar) {
        LaunchedEffect(showSnackbar) {
            kotlinx.coroutines.delay(3000)
            showSnackbar = false
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(snackbarMessage)
            }
        }
    }
}

private fun sendSmsWithManager(
    context: android.content.Context,
    phoneNumber: String,
    message: String,
    onStatusUpdate: (String) -> Unit
) {
    try {
        val smsManager = SmsManager.getDefault()
        val sentIntent = Intent("SMS_SENT")
        val sentPI = PendingIntent.getBroadcast(
            context,
            0,
            sentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deliveredIntent = Intent("SMS_DELIVERED")
        val deliveredPI = PendingIntent.getBroadcast(
            context,
            0,
            deliveredIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Send SMS with status tracking
        if (message.length <= 160) {
            smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onStatusUpdate("SMS send error: ${e.message}")
    }
}
