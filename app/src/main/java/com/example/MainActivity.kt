package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: BePresentViewModel

    // Broadcast receiver for pause / resume notification actions
    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (::viewModel.isInitialized) {
                viewModel.onBroadcastActionReceived(action)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register dynamic broadcast receiver
        val filter = IntentFilter().apply {
            addAction(NotificationHelper.ACTION_PAUSE)
            addAction(NotificationHelper.ACTION_RESUME)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationActionReceiver, filter)
        }

        setContent {
            BePresentTheme {
                viewModel = viewModel()

                // Trigger permission request for local notifications in Android 13+
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        Toast.makeText(context, "Notifications are disabled. We cannot show active tickers.", Toast.LENGTH_LONG).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // Handle incoming blocker redirect notifications
                var blockedAppPackageName by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(intent) {
                    blockedAppPackageName = intent?.getStringExtra("BLOCKED_FROM_PACKAGE")
                }

                BePresentApp(
                    viewModel = viewModel,
                    blockedOverlayApp = blockedAppPackageName,
                    onDismissBlockedOverlay = { blockedAppPackageName = null }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(notificationActionReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun BePresentApp(
    viewModel: BePresentViewModel,
    blockedOverlayApp: String?,
    onDismissBlockedOverlay: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("home") }

    // Collect variables dynamically from ViewModel using reactive StateFlows
    val pointsBalance by viewModel.pointsBalance.collectAsState()
    val streakDays by viewModel.streakDays.collectAsState()
    val focusScore by viewModel.focusScore.collectAsState()
    val sessionsToday by viewModel.sessionsTodayCount.collectAsState()
    val screenUsedMinutes by viewModel.screenUsedMinutes.collectAsState()
    val isZenUnlocked by viewModel.isZenMasterSuffixUnlocked.collectAsState()
    val isGoldUnlocked by viewModel.isGoldenThemeUnlocked.collectAsState()
    val isCelestialUnlocked by viewModel.isCelestialGlowUnlocked.collectAsState()

    // Active theme colors
    val themeAccentColor = if (isGoldUnlocked) AccentGold else ElectricTeal

    // User display name customization
    val baseName = "Junior"
    val displayName = if (isZenUnlocked) "$baseName the Zen Master" else baseName

    // Active Timer states from ViewModel
    val activeMode by viewModel.activeMode.collectAsState()
    val timerRemainingSeconds by viewModel.timerSecondsRemaining.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val totalTimerDurationSeconds by viewModel.totalTimerDurationSeconds.collectAsState()

    // Trigger navigation and autoplay Pomodoro when Home CTA is tapped
    val triggerStartFocusSession: () -> Unit = {
        viewModel.changeActiveMode(FocusMode.WORK)
        viewModel.startTimer()
        currentTab = "focus"
        Toast.makeText(context, "Shield Active: Work Mode countdown starting...", Toast.LENGTH_SHORT).show()
    }

    // Dynamic UI background color palette matching wellness mode
    Scaffold(
        bottomBar = {
            BePresentBottomBar(
                currentTab = currentTab,
                onTabSelected = { 
                    currentTab = it 
                    if (it == "stats") {
                        viewModel.refreshUsageStats()
                    }
                },
                accentColor = themeAccentColor
            )
        },
        containerColor = DarkMidnightBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(300),
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    "home" -> HomeScreen(
                        displayName = displayName,
                        streakDays = streakDays,
                        pointsBalance = pointsBalance,
                        focusScore = focusScore,
                        sessionsToday = sessionsToday,
                        screenUsedMinutes = screenUsedMinutes,
                        screenGoalMinutes = 180, // Default 3 hours goal
                        isTimerRunning = isTimerRunning,
                        timerRemainingSeconds = timerRemainingSeconds,
                        isCelestialGlowActive = isCelestialUnlocked,
                        onStartFocusClicked = triggerStartFocusSession,
                        onViewActiveSessionClicked = { currentTab = "focus" },
                        accentColor = themeAccentColor
                    )
                    "focus" -> {
                        // Check if accessibility shield blocker service is active
                        val isServiceActive = FocusAccessibilityService.isServiceEnabled(context)
                        FocusScreen(
                            isTimerRunning = isTimerRunning,
                            timerRemainingSeconds = timerRemainingSeconds,
                            timerTotalSeconds = totalTimerDurationSeconds,
                            activeMode = activeMode,
                            isAccessibilityActive = isServiceActive,
                            onModeChanged = { viewModel.changeActiveMode(it) },
                            onToggleTimer = {
                                if (isTimerRunning) viewModel.pauseTimer() else viewModel.startTimer()
                            },
                            onResetTimer = { viewModel.resetTimer() },
                            onLaunchSettingsClicked = {
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                    Toast.makeText(context, "Find 'BePresent' in Settings and click ON.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Accessibility settings is unavailable.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            accentColor = themeAccentColor
                        )
                    }
                    "stats" -> {
                        val isStatsPermGranted = viewModel.isUsageStatsPermissionGranted()
                        val appsStatsList by viewModel.appUsageStats.collectAsState()
                        StatsScreen(
                            isPermissionGranted = isStatsPermGranted,
                            appStats = appsStatsList,
                            onLaunchPermissionsClicked = {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            },
                            accentColor = themeAccentColor
                        )
                    }
                    "rewards" -> {
                        val level by viewModel.currentLevel.collectAsState()
                        val xp by viewModel.currentXp.collectAsState()
                        RewardsScreen(
                            pointsBalance = pointsBalance,
                            level = level,
                            xp = xp,
                            nextLevelXp = viewModel.nextLevelXp,
                            isZenUnlocked = isZenUnlocked,
                            isGoldUnlocked = isGoldUnlocked,
                            isCelestialUnlocked = isCelestialUnlocked,
                            onBuyZenMaster = { viewModel.buyZenMaster() },
                            onBuyGoldTheme = { viewModel.buyGoldenTheme() },
                            onBuyCelestialGlow = { viewModel.buyCelestialGlow() },
                            accentColor = themeAccentColor
                        )
                    }
                }
            }

            // Real-time dynamic overlay warning displayed when blocker catches distracting app accesses
            if (blockedOverlayApp != null) {
                FocusBlockerOverlay(
                    blockedAppLabel = blockedOverlayApp.substringAfterLast("."),
                    accentColor = themeAccentColor,
                    onDismiss = onDismissBlockedOverlay,
                    onEndSession = {
                        FocusAccessibilityService.isFocusActive = false
                        viewModel.pauseTimer()
                        onDismissBlockedOverlay()
                    }
                )
            }
        }
    }
}

// Helper to determine greeting text depending on dynamic system clock hours
fun getGreetingHeader(): String {
    val currentHour = LocalTime.now().hour
    return when {
        currentHour in 5..11 -> "Good morning"
        currentHour in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}

// ==================== SCREEN 1: HOME COMPOSABLE ====================
@Composable
fun HomeScreen(
    displayName: String,
    streakDays: Int,
    pointsBalance: Int,
    focusScore: Int,
    sessionsToday: Int,
    screenUsedMinutes: Int,
    screenGoalMinutes: Int,
    isTimerRunning: Boolean,
    timerRemainingSeconds: Int,
    isCelestialGlowActive: Boolean,
    onStartFocusClicked: () -> Unit,
    onViewActiveSessionClicked: () -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Aesthetic Edge-aligned Greeting Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${getGreetingHeader()},",
                    color = MutedNavyGray,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp)
                )
                Text(
                    text = "Focus Ninja — Level 4",
                    color = accentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Avatar frame
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CardBackground)
                    .border(
                        BorderStroke(
                            width = 2.dp,
                            color = if (isCelestialGlowActive) GlowPremiumBlue else accentColor
                        ),
                        shape = CircleShape
                    )
            ) {
                if (isCelestialGlowActive) {
                    val infiniteTransition = rememberInfiniteTransition(label = "profilePulse")
                    val pulseScale = infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulser"
                    )
                    Canvas(modifier = Modifier.fillMaxSize().scale(pulseScale.value)) {
                        drawCircle(
                            color = GlowPremiumBlue.copy(alpha = 0.2f),
                            radius = size.minDimension / 2
                        )
                    }
                }
                Text(
                    text = "J",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Screen Time Donut container card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's screen limit",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Focus: $focusScore",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ScreenTimeRing(
                    usedMinutes = screenUsedMinutes,
                    goalMinutes = screenGoalMinutes,
                    accentColor = accentColor
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                val remMins = (screenGoalMinutes - screenUsedMinutes).coerceAtLeast(0)
                Text(
                    text = "Remaining phone allowance: " + 
                           "${remMins / 60}h ${remMins % 60}m",
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Daily fast review metrics indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InfoMiniMetrics(
                label = "Sessions Today",
                value = "$sessionsToday completed",
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f),
                iconTint = ElectricTeal
            )
            InfoMiniMetrics(
                label = "Streak Limit",
                value = "$streakDays days 🔥",
                icon = Icons.Default.Whatshot,
                modifier = Modifier.weight(1f),
                iconTint = SoftWarningAmber
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Timer controller active warning
        if (isTimerRunning) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.5.dp, accentColor),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onViewActiveSessionClicked() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulseGlow")
                            val pulseCoeff = infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .scale(pulseCoeff.value)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.4f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            val currentMinutesRemaining = timerRemainingSeconds / 60
                            val currentSecondsRemaining = timerRemainingSeconds % 60
                            Text(
                                text = "Focus Session Engaged",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = String.format("%02d:%02d remaining", currentMinutesRemaining, currentSecondsRemaining),
                                color = accentColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "View",
                        tint = MutedNavyGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Button(
                onClick = onStartFocusClicked,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_focus_home_cta")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = DarkMidnightBg,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Focus Session (+50 XP)",
                        color = DarkMidnightBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ==================== SCREEN 2: FOCUS CONTROLLER ====================
@Composable
fun FocusScreen(
    isTimerRunning: Boolean,
    timerRemainingSeconds: Int,
    timerTotalSeconds: Int,
    activeMode: FocusMode,
    isAccessibilityActive: Boolean,
    onModeChanged: (FocusMode) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onLaunchSettingsClicked: () -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Focus Station",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Block distracting apps and earn focus rewards instantly.",
            color = MutedNavyGray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Session type selector segmental widgets
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FocusMode.values().forEach { mode ->
                    val isSelected = activeMode == mode
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) accentColor else Color.Transparent)
                            .clickable { onModeChanged(mode) }
                            .padding(vertical = 10.dp)
                            .testTag("mode_${mode.name.lowercase()}_pill")
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) DarkMidnightBg else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Radial watch segment
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            FocusCircularCountdownRing(
                secondsRemaining = timerRemainingSeconds,
                totalSeconds = timerTotalSeconds,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            val earnPointsAmount = if (activeMode == FocusMode.WORK) 50 else 20
            Surface(
                color = accentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "You'll earn +$earnPointsAmount pts for this session",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onResetTimer,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardBackground)
                        .border(1.dp, CardBorder, CircleShape)
                        .testTag("reset_timer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Timer",
                        tint = Color.White
                    )
                }

                Button(
                    onClick = onToggleTimer,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 150.dp)
                        .testTag("play_pause_timer_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isTimerRunning) "Pause" else "Start",
                            tint = DarkMidnightBg,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTimerRunning) "Pause" else "Start Focused",
                            color = DarkMidnightBg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shield guard status check card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isAccessibilityActive) Color(0xFF112520) else Color(0xFF28181A)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isAccessibilityActive) ElectricTeal.copy(alpha = 0.3f) else BlockCoral.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAccessibilityActive) ElectricTeal.copy(alpha = 0.15f)
                                else BlockCoral.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = if (isAccessibilityActive) Icons.Default.Security else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isAccessibilityActive) ElectricTeal else BlockCoral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isAccessibilityActive) "Screen Shield Shield Engaged" else "Shield Setup Required",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isAccessibilityActive) {
                        "BePresent is successfully monitoring and auto-blocking distracting apps (Instagram, TikTok, YouTube etc) during work timers."
                    } else {
                        "To prevent launching distracting apps in focus mode, please enable BePresent's helper service in Android Settings."
                    },
                    color = MutedNavyGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                if (!isAccessibilityActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onLaunchSettingsClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = BlockCoral),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Enable App Shield in Settings 🔑",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ==================== SCREEN 3: ANALYTICS & INSIGHTS ====================
@Composable
fun StatsScreen(
    isPermissionGranted: Boolean,
    appStats: List<AppUsageInfo>,
    onLaunchPermissionsClicked: () -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Usage Insights",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Analyze screen limits & physical habits over the past week.",
            color = MutedNavyGray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Direct Pure-Compose SVG/Canvas weekly bar metrics
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Weekly Screen usage",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Averaged 2.8 hours daily",
                    color = MutedNavyGray,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                WeeklyUsageBarChart(accentColor = accentColor)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // System Usage permission guide card
        if (!isPermissionGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF261C12)),
                border = BorderStroke(width = 1.dp, color = SoftWarningAmber.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunchPermissionsClicked() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SoftWarningAmber.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SoftWarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Real Screen-Time Statistics",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Tap to grant Usage Permission for real device metrics.",
                            color = MutedNavyGray,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open",
                        tint = SoftWarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Top App usage statistics
        Text(
            text = if (isPermissionGranted) "Real App Usages Today" else "Most Used Apps (Mock)",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                appStats.forEachIndexed { idx, app ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.appName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = app.durationHoursAndMins,
                                color = MutedNavyGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Progress bar block with gorgeous linear gradient
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CardBorder)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(app.usagePercentage)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(accentColor.copy(alpha = 0.5f), accentColor)
                                        )
                                    )
                            )
                        }
                    }
                    if (idx < appStats.size - 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Insight Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎉",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "Weekly Focus Insight",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "You used your phone 23% less than last week! That's equivalent to reclaiming 4 hours of your life.",
                        color = MutedNavyGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ==================== SCREEN 4: REWARDS SHOP & GAMIFICATION ====================
@Composable
fun RewardsScreen(
    pointsBalance: Int,
    level: Int,
    xp: Int,
    nextLevelXp: Int,
    isZenUnlocked: Boolean,
    isGoldUnlocked: Boolean,
    isCelestialUnlocked: Boolean,
    onBuyZenMaster: () -> Unit,
    onBuyGoldTheme: () -> Unit,
    onBuyCelestialGlow: () -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Gamified Rewards",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Redeem earned focus points to customize your identity.",
            color = MutedNavyGray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Level Profile Stats Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(2.dp, accentColor, CircleShape)
                ) {
                    Text(
                        text = "Lvl\n$level",
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "Focus Ninja",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "$pointsBalance pts total",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Progress XP block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(CardBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(xp.toFloat() / nextLevelXp.toFloat())
                                .clip(RoundedCornerShape(5.dp))
                                .background(accentColor)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$xp / $nextLevelXp XP to Level ${level + 1}",
                        color = MutedNavyGray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cosmetics customizations purchase shop
        Text(
            text = "Cosmetics Shop 🛍️",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        ShopItemCard(
            title = "Identity: 'the Zen Master'",
            desc = "Alter your profile name on the main lounge screen with a custom suffixes.",
            pointsCost = 300,
            isUnlocked = isZenUnlocked,
            userTotalPoints = pointsBalance,
            onRedeemClicked = onBuyZenMaster,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        ShopItemCard(
            title = "Golden Focus Skin",
            desc = "Ditch Electric Teal! Swap theme colors across screens for a luxury Gold skin.",
            pointsCost = 500,
            isUnlocked = isGoldUnlocked,
            userTotalPoints = pointsBalance,
            onRedeemClicked = onBuyGoldTheme,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        ShopItemCard(
            title = "Celestial Avatar Glow",
            desc = "Wraps your lounge avatar frame with a celestial neon breathing light pulse.",
            pointsCost = 800,
            isUnlocked = isCelestialUnlocked,
            userTotalPoints = pointsBalance,
            onRedeemClicked = onBuyCelestialGlow,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Friend Leaderboard blurred overlay element
        Text(
            text = "Global Leaderboard",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .blur(5.dp)
            ) {
                LeaderboardRow(rank = "1", name = "Satoshi Focus", points = "3,250 pts")
                HorizontalDivider(color = CardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                LeaderboardRow(rank = "2", name = "Junior (You)", points = "$pointsBalance pts")
                HorizontalDivider(color = CardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                LeaderboardRow(rank = "3", name = "Zen Archer", points = "985 pts")
            }

            // Overlay Lock prompt
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Unlocks at focus Level 5",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Complete further daily work blocks to compete with friends.",
                        color = MutedNavyGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Custom Shop item card
@Composable
fun ShopItemCard(
    title: String,
    desc: String,
    pointsCost: Int,
    isUnlocked: Boolean,
    userTotalPoints: Int,
    onRedeemClicked: () -> Unit,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUnlocked) accentColor.copy(alpha = 0.5f) else CardBorder
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = desc,
                    color = MutedNavyGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isUnlocked) {
                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Unlocked",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            } else {
                Button(
                    onClick = onRedeemClicked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (userTotalPoints >= pointsCost) accentColor else CardBorder
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    elevation = null,
                    modifier = Modifier.widthIn(min = 80.dp)
                ) {
                    Text(
                        text = "$pointsCost pts",
                        color = if (userTotalPoints >= pointsCost) DarkMidnightBg else MutedNavyGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LeaderboardRow(rank: String, name: String, points: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#$rank",
                color = ElectricTeal,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.width(26.dp)
            )
            Text(
                text = name,
                color = Color.White,
                fontSize = 13.sp
            )
        }
        Text(
            text = points,
            color = MutedNavyGray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ==================== VISUAL BLOCKER OVERLAY LAYOUT ====================
@Composable
fun FocusBlockerOverlay(
    blockedAppLabel: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onEndSession: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(enabled = false) {} // block click intercepts
            .padding(24.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(2.dp, BlockCoral),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(BlockCoral.copy(alpha = 0.15f))
                        .border(3.dp, BlockCoral, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = BlockCoral,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "App Blocked",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Distracting app is blocked during focus mode.",
                    color = MutedNavyGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "I'll stay focused 💪",
                        color = DarkMidnightBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onEndSession
                ) {
                    Text(
                        text = "End Focus Session",
                        color = BlockCoral,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ==================== REUSABLE CHART MODULES ====================
@Composable
fun WeeklyUsageBarChart(accentColor: Color) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val usages = listOf(4.2f, 3.1f, 2.8f, 3.9f, 2.1f, 5.2f, 1.4f)
    val maxLimitHours = 6f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        days.zip(usages).forEach { (day, h) ->
            val fillCoeff = (h / maxLimitHours).coerceIn(0.05f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${h}h",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                // Single bar frame
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(CardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fillCoeff)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(accentColor, accentColor.copy(alpha = 0.5f))
                                )
                            )
                            .align(Alignment.BottomCenter)
                    )
                }

                Text(
                    text = day,
                    color = MutedNavyGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==================== MISSING CUSTOM UI COMPONENTS ====================

@Composable
fun BePresentBottomBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    accentColor: Color
) {
    NavigationBar(
        containerColor = CardBackground,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier.border(
            BorderStroke(1.dp, CardBorder),
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        )
    ) {
        NavigationBarItem(
            selected = currentTab == "home",
            onClick = { onTabSelected("home") },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DarkMidnightBg,
                selectedTextColor = accentColor,
                indicatorColor = accentColor,
                unselectedIconColor = MutedNavyGray,
                unselectedTextColor = MutedNavyGray
            ),
            modifier = Modifier.testTag("tab_button_home")
        )
        NavigationBarItem(
            selected = currentTab == "focus",
            onClick = { onTabSelected("focus") },
            icon = { Icon(Icons.Outlined.Timer, contentDescription = "Focus") },
            label = { Text("Focus") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DarkMidnightBg,
                selectedTextColor = accentColor,
                indicatorColor = accentColor,
                unselectedIconColor = MutedNavyGray,
                unselectedTextColor = MutedNavyGray
            ),
            modifier = Modifier.testTag("tab_button_focus")
        )
        NavigationBarItem(
            selected = currentTab == "stats",
            onClick = { onTabSelected("stats") },
            icon = { Icon(Icons.Default.BarChart, contentDescription = "Stats") },
            label = { Text("Stats") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DarkMidnightBg,
                selectedTextColor = accentColor,
                indicatorColor = accentColor,
                unselectedIconColor = MutedNavyGray,
                unselectedTextColor = MutedNavyGray
            ),
            modifier = Modifier.testTag("tab_button_stats")
        )
        NavigationBarItem(
            selected = currentTab == "rewards",
            onClick = { onTabSelected("rewards") },
            icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Rewards") },
            label = { Text("Rewards") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DarkMidnightBg,
                selectedTextColor = accentColor,
                indicatorColor = accentColor,
                unselectedIconColor = MutedNavyGray,
                unselectedTextColor = MutedNavyGray
            ),
            modifier = Modifier.testTag("tab_button_rewards")
        )
    }
}

@Composable
fun ScreenTimeRing(
    usedMinutes: Int,
    goalMinutes: Int,
    accentColor: Color
) {
    val progress = (usedMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            // Background track circle
            drawArc(
                color = CardBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
            // Progress segment circle
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val remaining = (goalMinutes - usedMinutes).coerceAtLeast(0)
            val hrs = remaining / 60
            val mins = remaining % 60
            Text(
                text = String.format("%dh %02dm", hrs, mins),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Left today",
                color = MutedNavyGray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun InfoMiniMetrics(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = ElectricTeal
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    color = MutedNavyGray,
                    fontSize = 11.sp,
                )
                Text(
                    text = value,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun FocusCircularCountdownRing(
    secondsRemaining: Int,
    totalSeconds: Int,
    accentColor: Color
) {
    val progress = (secondsRemaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(220.dp)
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            drawArc(
                color = CardBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val mins = secondsRemaining / 60
            val secs = secondsRemaining % 60
            Text(
                text = String.format("%02d:%02d", mins, secs),
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1).sp
            )
            Text(
                text = "Remaining",
                color = MutedNavyGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
