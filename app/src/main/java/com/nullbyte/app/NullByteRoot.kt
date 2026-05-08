package com.nullbyte.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nullbyte.app.data.NullBytePreferences
import com.nullbyte.app.notifications.NotificationScheduler
import com.nullbyte.app.ui.screens.HomeScreen
import com.nullbyte.app.ui.screens.OnboardingScreen
import com.nullbyte.app.ui.screens.ReviewScreen
import com.nullbyte.app.ui.screens.SettingsScreen
import com.nullbyte.app.ui.theme.NullByteTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NullByteRoot(
    sharedUris: StateFlow<List<Uri>>,
    tutorialRequests: StateFlow<Int>,
    onSharedUrisConsumed: () -> Unit,
    onTutorialRequestConsumed: () -> Unit,
) {
    val incomingUris by sharedUris.collectAsState()
    val pendingTutorialRequests by tutorialRequests.collectAsState()
    val context = LocalContext.current
    val prefs = remember { NullBytePreferences(context) }
    val scope = rememberCoroutineScope()

    var hasSeenOnboarding by rememberSaveable { mutableStateOf(prefs.hasSeenOnboarding()) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(prefs.notificationsEnabled()) }
    var remindersEnabled by rememberSaveable { mutableStateOf(prefs.remindersEnabled()) }
    var showTutorialOnLaunch by rememberSaveable { mutableStateOf(prefs.showTutorialOnLaunch()) }
    var reviewUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var previousContentScreen by rememberSaveable { mutableStateOf(Screen.Home) }
    var pendingReminderEnable by rememberSaveable { mutableStateOf(false) }
    var currentScreen by rememberSaveable {
        mutableStateOf(
            if (!hasSeenOnboarding || showTutorialOnLaunch) {
                Screen.Onboarding
            } else {
                Screen.Home
            },
        )
    }

    val navigateTo: (Screen) -> Unit = { screen ->
        previousContentScreen = screen
        currentScreen = screen
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            reviewUris = uris.distinct()
            navigateTo(Screen.Review)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val enabled = granted || Build.VERSION.SDK_INT < 33
        notificationsEnabled = enabled
        prefs.setNotificationsEnabled(enabled)

        if (enabled && pendingReminderEnable) {
            remindersEnabled = true
            prefs.setRemindersEnabled(true)
        } else if (!enabled) {
            remindersEnabled = false
            prefs.setRemindersEnabled(false)
        }

        pendingReminderEnable = false
    }

    fun requestNotificationAccess(enableReminderAfterGrant: Boolean) {
        pendingReminderEnable = enableReminderAfterGrant

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationsEnabled = true
            prefs.setNotificationsEnabled(true)
            if (enableReminderAfterGrant) {
                remindersEnabled = true
                prefs.setRemindersEnabled(true)
            }
            pendingReminderEnable = false
        }
    }

    fun setNotifications(enabled: Boolean) {
        if (!enabled) {
            notificationsEnabled = false
            remindersEnabled = false
            prefs.setNotificationsEnabled(false)
            prefs.setRemindersEnabled(false)
            pendingReminderEnable = false
            return
        }

        requestNotificationAccess(enableReminderAfterGrant = false)
    }

    fun setReminders(enabled: Boolean) {
        if (!enabled) {
            remindersEnabled = false
            prefs.setRemindersEnabled(false)
            return
        }

        if (!notificationsEnabled) {
            requestNotificationAccess(enableReminderAfterGrant = true)
        } else {
            remindersEnabled = true
            prefs.setRemindersEnabled(true)
        }
    }

    fun openTutorial(fromScreen: Screen = currentScreen) {
        previousContentScreen = if (fromScreen == Screen.Onboarding) Screen.Home else fromScreen
        currentScreen = Screen.Onboarding
    }

    fun pickMedia() {
        mediaPickerLauncher.launch(
            arrayOf(
                "image/*",
                "video/mp4",
                "video/quicktime",
                "video/3gpp",
                "audio/mpeg",
                "audio/mp4",
                "audio/x-m4a",
                "audio/wav",
                "audio/x-wav",
            ),
        )
    }

    LaunchedEffect(incomingUris) {
        if (incomingUris.isNotEmpty()) {
            reviewUris = incomingUris.distinct()
            navigateTo(Screen.Review)
            onSharedUrisConsumed()
        }
    }

    LaunchedEffect(pendingTutorialRequests) {
        if (pendingTutorialRequests > 0) {
            openTutorial(
                if (currentScreen == Screen.Onboarding) {
                    previousContentScreen
                } else {
                    currentScreen
                },
            )
            onTutorialRequestConsumed()
        }
    }

    LaunchedEffect(remindersEnabled, notificationsEnabled) {
        if (remindersEnabled && notificationsEnabled) {
            NotificationScheduler.scheduleDailyReminder(context)
        } else {
            NotificationScheduler.cancelDailyReminder(context)
        }
    }

    NullByteTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (currentScreen == Screen.Onboarding) {
                OnboardingScreen(
                    canClose = hasSeenOnboarding || reviewUris.isNotEmpty() || showTutorialOnLaunch,
                    onClose = {
                        currentScreen = if (reviewUris.isNotEmpty() && previousContentScreen == Screen.Review) {
                            Screen.Review
                        } else {
                            previousContentScreen
                        }
                    },
                    onFinish = {
                        hasSeenOnboarding = true
                        prefs.setHasSeenOnboarding(true)
                        currentScreen = if (reviewUris.isNotEmpty() && previousContentScreen == Screen.Review) {
                            Screen.Review
                        } else {
                            previousContentScreen
                        }
                    },
                )
            } else {
                val drawerState = rememberDrawerState(DrawerValue.Closed)

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text(
                                text = "NullByte",
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )

                            NavigationDrawerItem(
                                label = { Text("Home") },
                                selected = currentScreen == Screen.Home,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navigateTo(Screen.Home)
                                },
                            )

                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = if (reviewUris.isEmpty()) {
                                            "Review queue"
                                        } else {
                                            "Review queue (${reviewUris.size})"
                                        },
                                    )
                                },
                                selected = currentScreen == Screen.Review,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navigateTo(Screen.Review)
                                },
                            )

                            NavigationDrawerItem(
                                label = { Text("Settings") },
                                selected = currentScreen == Screen.Settings,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navigateTo(Screen.Settings)
                                },
                            )

                            NavigationDrawerItem(
                                label = { Text("Guide") },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    openTutorial(currentScreen)
                                },
                            )
                        }
                    },
                ) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text(currentScreen.title) },
                                navigationIcon = {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                drawerState.open()
                                            }
                                        },
                                    ) {
                                        Text("Menu")
                                    }
                                },
                            )
                        },
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        ) {
                            when (currentScreen) {
                                Screen.Home -> {
                                    HomeScreen(
                                        selectedBatchCount = reviewUris.size,
                                        onPickMedia = { pickMedia() },
                                        onOpenReview = { navigateTo(Screen.Review) },
                                        onOpenSettings = { navigateTo(Screen.Settings) },
                                        onReviewTutorial = { openTutorial(currentScreen) },
                                    )
                                }

                                Screen.Review -> {
                                    ReviewScreen(
                                        selectedUris = reviewUris,
                                        notificationsEnabled = notificationsEnabled,
                                        onPickAnotherBatch = { pickMedia() },
                                    )
                                }

                                Screen.Settings -> {
                                    SettingsScreen(
                                        notificationsEnabled = notificationsEnabled,
                                        remindersEnabled = remindersEnabled,
                                        showTutorialOnLaunch = showTutorialOnLaunch,
                                        onNotificationsChanged = { enabled -> setNotifications(enabled) },
                                        onRemindersChanged = { enabled -> setReminders(enabled) },
                                        onShowTutorialOnLaunchChanged = { enabled ->
                                            showTutorialOnLaunch = enabled
                                            prefs.setShowTutorialOnLaunch(enabled)
                                        },
                                        onOpenTutorial = { openTutorial(currentScreen) },
                                    )
                                }

                                Screen.Onboarding -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen(val title: String) {
    Onboarding("Guide"),
    Home("Home"),
    Review("Review Queue"),
    Settings("Settings"),
}
