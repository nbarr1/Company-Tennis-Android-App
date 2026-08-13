package com.example.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Channel
import com.example.data.models.Message
import com.example.data.models.SharedContact
import com.example.data.repository.TennisRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen() {
    val channels by TennisRepository.channels.collectAsState()
    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    if (selectedChannelId != null) {
        ChatThreadView(
            channelId = selectedChannelId!!,
            onBack = { selectedChannelId = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("In-App Messages", fontWeight = FontWeight.Bold) })
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(channels) { channel ->
                    ChannelCardItem(
                        channel = channel,
                        onClick = { selectedChannelId = channel.id }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelCardItem(channel: Channel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("channel_item_${channel.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (channel.type == "division") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (channel.type == "division") Icons.Default.Campaign else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (channel.type == "division") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name ?: "Message Channel",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = channel.lastMessage?.content ?: "No messages yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadView(channelId: String, onBack: () -> Unit) {
    val allMessages = remember(channelId) { TennisRepository.getMessages(channelId) }
    var messageText by remember { mutableStateOf("") }
    val user by TennisRepository.currentUser.collectAsState()
    val blockedUserIds = user?.blockedUserIds ?: emptyList()
    val messages = remember(allMessages, blockedUserIds) {
        allMessages.filterNot { it.senderId in blockedUserIds }
    }

    var reportDialogMessage by remember { mutableStateOf<Message?>(null) }
    var blockDialogUser by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Match & Division Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            user?.let { u ->
                                TennisRepository.sendMessage(
                                    channelId = channelId,
                                    content = "Shared contact details for match scheduling",
                                    type = "contact_share",
                                    contact = SharedContact(phone = u.phone, email = u.email)
                                )
                            }
                        },
                        modifier = Modifier.testTag("share_contact_button")
                    ) {
                        Icon(Icons.Default.ContactPhone, contentDescription = "Share Contact")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                TennisRepository.sendMessage(channelId, messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier.testTag("send_message_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == user?.id
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg.senderName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)

                            if (msg.type == "contact_share" && msg.sharedContact != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        msg.sharedContact.phone?.let { Text("📞 $it", style = MaterialTheme.typography.bodySmall) }
                                        msg.sharedContact.email?.let { Text("✉️ $it", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                        }
                    }

                    if (!isMe) {
                        Row {
                            TextButton(
                                onClick = { reportDialogMessage = msg },
                                modifier = Modifier.testTag("report_message_${msg.id}"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = "Report", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Report", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = { blockDialogUser = msg.senderId to msg.senderName },
                                modifier = Modifier.testTag("block_user_${msg.senderId}"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Block, contentDescription = "Block", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Block", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    reportDialogMessage?.let { msg ->
        ReportMessageDialog(
            onDismiss = { reportDialogMessage = null },
            onConfirm = { reason ->
                TennisRepository.reportMessage(channelId, msg, reason)
                reportDialogMessage = null
            }
        )
    }

    blockDialogUser?.let { (blockedUserId, blockedUserName) ->
        AlertDialog(
            onDismissRequest = { blockDialogUser = null },
            title = { Text("Block $blockedUserName?") },
            text = { Text("You won't see messages from $blockedUserName anymore. You can unblock them later from your profile.") },
            confirmButton = {
                Button(
                    onClick = {
                        TennisRepository.blockUser(blockedUserId)
                        blockDialogUser = null
                    },
                    modifier = Modifier.testTag("confirm_block_user_button")
                ) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { blockDialogUser = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReportMessageDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val reasons = listOf("Harassment", "Spam", "Inappropriate", "Other")
    var selectedReason by remember { mutableStateOf(reasons.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Why are you reporting this message?", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                reasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .testTag("report_reason_$reason")
                    ) {
                        RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason })
                        Text(reason)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedReason) },
                modifier = Modifier.testTag("submit_report_button")
            ) { Text("Submit Report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
