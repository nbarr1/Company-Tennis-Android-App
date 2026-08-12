package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.data.repository.TennisRepository
import com.example.ui.admin.AdminScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.feedback.FeedbackScreen
import com.example.ui.home.HomeScreen
import com.example.ui.matches.CreateMatchScreen
import com.example.ui.matches.MatchDetailScreen
import com.example.ui.matches.MatchHistoryScreen
import com.example.ui.matches.MatchesScreen
import com.example.ui.messages.MessagesScreen
import com.example.ui.onboarding.DivisionJoinCreateScreen
import com.example.ui.onboarding.TutorialScreen
import com.example.firebase.FirebaseInitializer
import com.example.ui.admin.MatchSchedulerScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.UserProfileScreen
import com.example.ui.rankings.RankingsScreen
import com.example.ui.theme.TennisLeagueTheme
import com.example.wear.WearOsManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase SDK
        FirebaseInitializer.init(this)

        // Initialize Wear OS Manager for Watch live score synchronization
        WearOsManager.init(this)

        setContent {
            TennisLeagueTheme {
                MainAppEntry()
            }
        }
    }
}

object ScreenRoutes {
    const val AUTH = "login"
    const val DIVISION_SETUP = "division_setup"
    const val TUTORIAL = "tutorial"
    const val DASHBOARD = "dashboard"
    const val MATCH_DETAIL = "match_detail/{matchId}"
    const val CREATE_MATCH = "create_match"
    const val MATCH_HISTORY = "match_history"
    const val USER_PROFILE = "user_profile"
    const val MATCH_SCHEDULER = "match_scheduler"
    const val FEEDBACK = "feedback"
}

@Composable
fun MainAppEntry() {
    val user by TennisRepository.currentUser.collectAsState()
    val division by TennisRepository.currentDivision.collectAsState()

    val navController = rememberNavController()

    LaunchedEffect(division?.id) {
        division?.id?.let { divId ->
            TennisRepository.startRealtimeSync(divId)
        }
    }

    val targetRoute = remember(user, division) {
        when {
            user == null -> ScreenRoutes.AUTH
            user?.divisionId == null && division == null -> ScreenRoutes.DIVISION_SETUP
            user?.tutorialDone != true -> ScreenRoutes.TUTORIAL
            else -> ScreenRoutes.DASHBOARD
        }
    }

    LaunchedEffect(targetRoute) {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute != targetRoute && currentRoute != ScreenRoutes.MATCH_DETAIL && currentRoute != ScreenRoutes.CREATE_MATCH && currentRoute != ScreenRoutes.MATCH_HISTORY && currentRoute != ScreenRoutes.USER_PROFILE && currentRoute != ScreenRoutes.MATCH_SCHEDULER && currentRoute != ScreenRoutes.FEEDBACK) {
            navController.navigate(targetRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = targetRoute
    ) {
        composable(ScreenRoutes.AUTH) {
            AuthScreen(
                onAuthSuccess = {
                    // Automatically handled by user state observation
                }
            )
        }
        composable(ScreenRoutes.DIVISION_SETUP) {
            DivisionJoinCreateScreen(
                onDivisionReady = {
                    // Automatically handled by division state observation
                }
            )
        }
        composable(ScreenRoutes.TUTORIAL) {
            TutorialScreen(
                onTutorialComplete = {
                    // Automatically handled by tutorial state observation
                }
            )
        }
        composable(ScreenRoutes.DASHBOARD) {
            MainScaffold(
                onNavigateToMatchDetail = { matchId ->
                    navController.navigate("match_detail/$matchId")
                },
                onNavigateToCreateMatch = {
                    navController.navigate(ScreenRoutes.CREATE_MATCH)
                },
                onNavigateToMatchHistory = {
                    navController.navigate(ScreenRoutes.MATCH_HISTORY)
                },
                onNavigateToUserProfile = {
                    navController.navigate(ScreenRoutes.USER_PROFILE)
                },
                onNavigateToMatchScheduler = {
                    navController.navigate(ScreenRoutes.MATCH_SCHEDULER)
                },
                onNavigateToFeedback = {
                    navController.navigate(ScreenRoutes.FEEDBACK)
                },
                onLogout = {
                    TennisRepository.logout()
                    navController.navigate(ScreenRoutes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(ScreenRoutes.MATCH_DETAIL) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: ""
            MatchDetailScreen(
                matchId = matchId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(ScreenRoutes.CREATE_MATCH) {
            CreateMatchScreen(
                onNavigateBack = { navController.popBackStack() },
                onMatchCreated = { matchId ->
                    navController.popBackStack()
                    navController.navigate("match_detail/$matchId")
                }
            )
        }
        composable(ScreenRoutes.MATCH_HISTORY) {
            MatchHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMatchDetail = { matchId ->
                    navController.navigate("match_detail/$matchId")
                }
            )
        }
        composable(ScreenRoutes.USER_PROFILE) {
            UserProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(ScreenRoutes.MATCH_SCHEDULER) {
            MatchSchedulerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(ScreenRoutes.FEEDBACK) {
            FeedbackScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

sealed class BottomTab(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomTab("tab_home", "Home", Icons.Default.Home)
    object Matches : BottomTab("tab_matches", "Matches", Icons.Default.SportsTennis)
    object Rankings : BottomTab("tab_rankings", "Rankings", Icons.Default.Leaderboard)
    object Messages : BottomTab("tab_messages", "Messages", Icons.AutoMirrored.Filled.Chat)
    object Profile : BottomTab("tab_profile", "Profile", Icons.Default.Person)
    object Admin : BottomTab("tab_admin", "Admin", Icons.Default.AdminPanelSettings)
}

@Composable
fun MainScaffold(
    onNavigateToMatchDetail: (String) -> Unit,
    onNavigateToCreateMatch: () -> Unit,
    onNavigateToMatchHistory: () -> Unit,
    onNavigateToUserProfile: () -> Unit,
    onNavigateToMatchScheduler: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onLogout: () -> Unit
) {
    val user by TennisRepository.currentUser.collectAsState()
    val tabNavController = rememberNavController()

    val isAdminRole = user?.role in listOf("division_leader", "admin", "app_developer")

    val tabs = remember(isAdminRole) {
        if (isAdminRole) {
            listOf(BottomTab.Home, BottomTab.Matches, BottomTab.Rankings, BottomTab.Messages, BottomTab.Profile, BottomTab.Admin)
        } else {
            listOf(BottomTab.Home, BottomTab.Matches, BottomTab.Rankings, BottomTab.Messages, BottomTab.Profile)
        }
    }

    val currentBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: BottomTab.Home.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                tabNavController.navigate(tab.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontWeight = if (currentRoute == tab.route) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("nav_${tab.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = tabNavController,
                startDestination = BottomTab.Home.route
            ) {
                composable(BottomTab.Home.route) {
                    HomeScreen(
                        onNavigateToMatchDetail = onNavigateToMatchDetail,
                        onNavigateToProposeMatch = {
                            tabNavController.navigate(BottomTab.Matches.route)
                        },
                        onNavigateToFeedback = onNavigateToFeedback
                    )
                }
                composable(BottomTab.Matches.route) {
                    MatchesScreen(
                        onNavigateToMatchDetail = onNavigateToMatchDetail,
                        onNavigateToCreateMatch = onNavigateToCreateMatch,
                        onNavigateToMatchHistory = onNavigateToMatchHistory
                    )
                }
                composable(BottomTab.Rankings.route) {
                    RankingsScreen()
                }
                composable(BottomTab.Messages.route) {
                    MessagesScreen()
                }
                composable(BottomTab.Profile.route) {
                    ProfileScreen(
                        onLogout = onLogout,
                        onNavigateToMatchHistory = onNavigateToMatchHistory,
                        onNavigateToUserProfile = onNavigateToUserProfile
                    )
                }
                composable(BottomTab.Admin.route) {
                    AdminScreen(
                        onNavigateToMatchScheduler = onNavigateToMatchScheduler
                    )
                }
            }
        }
    }
}
