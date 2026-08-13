package com.example.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class PolicySection(val title: String, val body: String)

private val policySections = listOf(
    PolicySection(
        "Information we collect",
        "Account information (email address and display name) when you sign up with " +
            "email/password or Google Sign-In. An optional phone number if you add one to your " +
            "profile. An avatar picked from a small built-in set of preset images — the app never " +
            "accesses your camera or photo library. Your division membership, proposed/scheduled/" +
            "scored matches, rankings, and weekly availability. Messages you send in the app's " +
            "in-app chat with other division members — using \"Share Contact\" sends your phone " +
            "number and email to the other participant(s) in that conversation. Any text you submit " +
            "via the in-app Feedback form. A push-notification device token."
    ),
    PolicySection(
        "How we use this information",
        "Solely to run the league: authenticating you, matching you with your division, syncing " +
            "match/ranking data in real time, delivering push notifications, and letting you " +
            "communicate with other players in your division. We do not use your data for " +
            "advertising and do not sell or share it with third parties for their own marketing."
    ),
    PolicySection(
        "Third-party services",
        "The app is built on Google Firebase, which acts as our data processor: Firebase " +
            "Authentication (sign-in), Cloud Firestore (stores and syncs your profile, division, " +
            "match, ranking, and message data), Firebase Cloud Messaging (push notifications), and " +
            "Firebase Cloud Functions (feedback submissions and server-side league logic). The app " +
            "integrates no advertising or analytics SDKs and does not use the Advertising ID."
    ),
    PolicySection(
        "Data retention and deletion",
        "Your data is retained while your account is active. You can permanently delete your " +
            "account and personal data at any time from Profile → Delete Account. This removes " +
            "your account record, your profile, and your identifying details from your division " +
            "roster. Match and ranking records jointly owned with other players may be retained in " +
            "anonymized form so other players' history and standings stay accurate. You can also " +
            "sign out at any time from Profile → Log Out without deleting your data."
    ),
    PolicySection(
        "User-generated content",
        "Messages sent in the app's chat are visible to other members of the relevant division or " +
            "conversation. You can report an abusive or inappropriate message, or block another " +
            "user, directly from the chat screen. Reports are reviewed by your division's leaders/" +
            "admins, who can remove reported messages."
    ),
    PolicySection(
        "Security",
        "Data in transit to and from Firebase is encrypted (HTTPS/TLS). Access to your division's " +
            "data is limited to members of that division."
    ),
    PolicySection(
        "Contact us",
        "Questions about this policy or requests regarding your data can be sent through the " +
            "in-app Feedback form, or to your division's league administrator."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("privacy_policy_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Tennis League collects only the data needed to run your division's league. " +
                    "This page explains what's collected, how it's used, and how to delete it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            policySections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(text = section.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
