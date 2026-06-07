package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

// Focus Mode configurations
enum class FocusMode(val label: String, val minutes: Int) {
    WORK("Work", 25),
    SHORT_BREAK("Short Break", 5),
    LONG_BREAK("Long Break", 15)
}

data class FocusHistoryItem(
    val id: String,
    val title: String,
    val mode: FocusMode,
    val durationMin: Int,
    val timestampStr: String,
    val pointsEarned: Int
)

class BePresentViewModel(application: Application) : AndroidViewModel(application) {

    val dataStoreManager = DataStoreManager(application)
    private val usageStatsRepository = UsageStatsRepository(application)
    private val notificationHelper = NotificationHelper(application)

    // Exposed Live UI States from Flows
    private val _sessionHistoryList = MutableStateFlow<List<FocusHistoryItem>>(
        listOf(
            FocusHistoryItem("1", "Social Detox Reset", FocusMode.WORK, 25, "1 hour ago", 50),
            FocusHistoryItem("2", "Deep Coding Session", FocusMode.WORK, 25, "3 hours ago", 50),
            FocusHistoryItem("3", "Morning Study Flow", FocusMode.WORK, 25, "Today, 9:20 AM", 50)
        )
    )
    val sessionHistoryList: StateFlow<List<FocusHistoryItem>> = _sessionHistoryList.asStateFlow()

    private val _pointsBalance = MutableStateFlow(1240)
    val pointsBalance: StateFlow<Int> = _pointsBalance.asStateFlow()

    private val _streakDays = MutableStateFlow(8)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private val _focusScore = MutableStateFlow(73)
    val focusScore: StateFlow<Int> = _focusScore.asStateFlow()

    private val _sessionsTodayCount = MutableStateFlow(3)
    val sessionsTodayCount: StateFlow<Int> = _sessionsTodayCount.asStateFlow()

    private val _screenUsedMinutes = MutableStateFlow(107)
    val screenUsedMinutes: StateFlow<Int> = _screenUsedMinutes.asStateFlow()

    private val _isZenMasterSuffixUnlocked = MutableStateFlow(false)
    val isZenMasterSuffixUnlocked: StateFlow<Boolean> = _isZenMasterSuffixUnlocked.asStateFlow()

    private val _isGoldenThemeUnlocked = MutableStateFlow(false)
    val isGoldenThemeUnlocked: StateFlow<Boolean> = _isGoldenThemeUnlocked.asStateFlow()

    private val _isCelestialGlowUnlocked = MutableStateFlow(false)
    val isCelestialGlowUnlocked: StateFlow<Boolean> = _isCelestialGlowUnlocked.asStateFlow()

    private val _lastSessionDate = MutableStateFlow<String?>(null)
    val lastSessionDate: StateFlow<String?> = _lastSessionDate.asStateFlow()

    private val _lastOpenDate = MutableStateFlow<String?>(null)

    // Timer States
    private val _activeMode = MutableStateFlow(FocusMode.WORK)
    val activeMode: StateFlow<FocusMode> = _activeMode.asStateFlow()

    private val _timerSecondsRemaining = MutableStateFlow(FocusMode.WORK.minutes * 60)
    val timerSecondsRemaining: StateFlow<Int> = _timerSecondsRemaining.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _totalTimerDurationSeconds = MutableStateFlow(FocusMode.WORK.minutes * 60)
    val totalTimerDurationSeconds: StateFlow<Int> = _totalTimerDurationSeconds.asStateFlow()

    // Usage Stats List
    private val _appUsageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val appUsageStats: StateFlow<List<AppUsageInfo>> = _appUsageStats.asStateFlow()

    // Level & XP values
    private val _currentLevel = MutableStateFlow(4)
    val currentLevel: StateFlow<Int> = _currentLevel.asStateFlow()

    private val _currentXp = MutableStateFlow(1240)
    val currentXp: StateFlow<Int> = _currentXp.asStateFlow()
    val nextLevelXp = 2000

    init {
        // Collect flows to update initial state from DataStorePreferences
        viewModelScope.launch {
            dataStoreManager.pointsFlow.collect { _pointsBalance.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.streakFlow.collect { _streakDays.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.focusScoreFlow.collect { _focusScore.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.sessionsTodayFlow.collect { _sessionsTodayCount.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.screenUsedMinutesFlow.collect { _screenUsedMinutes.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.isZenMasterUnlockedFlow.collect { _isZenMasterSuffixUnlocked.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.isGoldenThemeUnlockedFlow.collect { _isGoldenThemeUnlocked.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.isCelestialGlowUnlockedFlow.collect { _isCelestialGlowUnlocked.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.lastSessionDateFlow.collect { lastSession ->
                _lastSessionDate.value = lastSession
            }
        }
        viewModelScope.launch {
            dataStoreManager.lastOpenDateFlow.collect { lastOpen ->
                _lastOpenDate.value = lastOpen
            }
        }
        viewModelScope.launch {
            combine(
                dataStoreManager.lastOpenDateFlow,
                dataStoreManager.lastSessionDateFlow,
                dataStoreManager.streakFlow
            ) { lastOpen, lastSession, streak ->
                Triple(lastOpen, lastSession, streak)
            }.first().let { (lastOpen, lastSession, streak) ->
                _streakDays.value = streak
                updateStreakOnAppOpen(lastSession)
                performDailyResetCheck(lastOpen)
            }
        }

        refreshUsageStats()
    }

    private fun performDailyResetCheck(lastOpen: String?) {
        val today = LocalDate.now().toString()
        if (lastOpen != null && lastOpen != today) {
            // New Day Reset!
            viewModelScope.launch {
                _sessionsTodayCount.value = 0
                dataStoreManager.saveSessionsToday(0)
                
                // Reset screen used
                if (usageStatsRepository.hasUsageStatsPermission()) {
                    val minutesReal = usageStatsRepository.getTotalTodayScreenMinutes()
                    _screenUsedMinutes.value = minutesReal
                    dataStoreManager.saveScreenUsedMinutes(minutesReal)
                } else {
                    _screenUsedMinutes.value = 0
                    dataStoreManager.saveScreenUsedMinutes(0)
                }
                
                dataStoreManager.saveLastOpenDate(today)
            }
        } else if (lastOpen == null) {
            viewModelScope.launch {
                dataStoreManager.saveLastOpenDate(today)
            }
        }
    }

    private fun updateStreakOnAppOpen(lastSessionDateStr: String?) {
        if (lastSessionDateStr.isNullOrEmpty()) return
        try {
            val lastDate = LocalDate.parse(lastSessionDateStr)
            val today = LocalDate.now()
            val daysGap = java.time.temporal.ChronoUnit.DAYS.between(lastDate, today)
            
            viewModelScope.launch {
                if (daysGap > 1L) {
                    _streakDays.value = 0
                    dataStoreManager.saveStreak(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun changeActiveMode(mode: FocusMode) {
        pauseTimer()
        _activeMode.value = mode
        _timerSecondsRemaining.value = mode.minutes * 60
        _totalTimerDurationSeconds.value = mode.minutes * 60
    }

    // Timer Job Management
    private var timerJob: kotlinx.coroutines.Job? = null

    fun startTimer() {
        if (_isTimerRunning.value) return
        _isTimerRunning.value = true
        FocusAccessibilityService.isFocusActive = true

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isTimerRunning.value && _timerSecondsRemaining.value > 0) {
                delay(1000L)
                if (_isTimerRunning.value) {
                    val currentRemaining = _timerSecondsRemaining.value - 1
                    _timerSecondsRemaining.value = currentRemaining

                    if (currentRemaining % 5 == 0) {
                        notificationHelper.showActiveFocusNotification(
                            minutesRemaining = currentRemaining / 60,
                            secondsRemaining = currentRemaining % 60,
                            modeName = _activeMode.value.label,
                            isPaused = false
                        )
                    }

                    if (currentRemaining <= 0) {
                        _isTimerRunning.value = false
                        FocusAccessibilityService.isFocusActive = false
                        notificationHelper.cancelActiveFocusNotification()
                        completeSession(_activeMode.value)

                        _timerSecondsRemaining.value = _activeMode.value.minutes * 60
                        _totalTimerDurationSeconds.value = _activeMode.value.minutes * 60
                    }
                }
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        FocusAccessibilityService.isFocusActive = false
        timerJob?.cancel()

        notificationHelper.showActiveFocusNotification(
            minutesRemaining = _timerSecondsRemaining.value / 60,
            secondsRemaining = _timerSecondsRemaining.value % 60,
            modeName = _activeMode.value.label,
            isPaused = true
        )
    }

    fun resetTimer() {
        _isTimerRunning.value = false
        FocusAccessibilityService.isFocusActive = false
        timerJob?.cancel()

        _timerSecondsRemaining.value = _activeMode.value.minutes * 60
        _totalTimerDurationSeconds.value = _activeMode.value.minutes * 60

        notificationHelper.cancelActiveFocusNotification()
    }

    fun onBroadcastActionReceived(action: String) {
        if (action == NotificationHelper.ACTION_PAUSE) {
            pauseTimer()
        } else if (action == NotificationHelper.ACTION_RESUME) {
            startTimer()
        }
    }

    private fun completeSession(mode: FocusMode) {
        val todayStr = LocalDate.now().toString()
        viewModelScope.launch {
            val pointsAdded = if (mode == FocusMode.WORK) 50 else 20
            
            val newItem = FocusHistoryItem(
                id = System.currentTimeMillis().toString(),
                title = if (mode == FocusMode.WORK) "Productive Core" else "Rejuvenation Break",
                mode = mode,
                durationMin = mode.minutes,
                timestampStr = "Just now",
                pointsEarned = pointsAdded
            )
            _sessionHistoryList.value = listOf(newItem) + _sessionHistoryList.value.take(9)

            val newPoints = _pointsBalance.value + pointsAdded
            _pointsBalance.value = newPoints
            dataStoreManager.savePoints(newPoints)

            val newXp = _currentXp.value + pointsAdded
            if (newXp >= nextLevelXp) {
                _currentLevel.value += 1
                _currentXp.value = newXp - nextLevelXp
            } else {
                _currentXp.value = newXp
            }

            val newSessionCount = _sessionsTodayCount.value + 1
            _sessionsTodayCount.value = newSessionCount
            dataStoreManager.saveSessionsToday(newSessionCount)

            val newScore = (_focusScore.value + 4).coerceAtMost(100)
            _focusScore.value = newScore
            dataStoreManager.saveFocusScore(newScore)

            if (mode == FocusMode.WORK) {
                val newMins = (_screenUsedMinutes.value - 15).coerceAtLeast(0)
                _screenUsedMinutes.value = newMins
                dataStoreManager.saveScreenUsedMinutes(newMins)
            }

            // Streak check
            val lastDateStr = _lastSessionDate.value
            val today = LocalDate.now()
            val newStreak = if (lastDateStr.isNullOrEmpty()) {
                1
            } else {
                try {
                    val lastDate = LocalDate.parse(lastDateStr)
                    val days = java.time.temporal.ChronoUnit.DAYS.between(lastDate, today)
                    if (days == 1L) {
                        _streakDays.value + 1
                    } else if (days > 1L) {
                        1
                    } else {
                        _streakDays.value
                    }
                } catch (e: Exception) {
                    1
                }
            }
            _streakDays.value = newStreak
            dataStoreManager.saveStreak(newStreak)

            _lastSessionDate.value = todayStr
            dataStoreManager.saveLastSessionDate(todayStr)

            notificationHelper.showSessionCompleteNotification(mode.label, pointsAdded)
        }
    }

    fun refreshUsageStats() {
        viewModelScope.launch {
            val stats = usageStatsRepository.getTodayAppUsage()
            _appUsageStats.value = stats

            if (usageStatsRepository.hasUsageStatsPermission()) {
                val totalMinutes = usageStatsRepository.getTotalTodayScreenMinutes()
                _screenUsedMinutes.value = totalMinutes
                dataStoreManager.saveScreenUsedMinutes(totalMinutes)
            }
        }
    }

    fun isUsageStatsPermissionGranted(): Boolean {
        return usageStatsRepository.hasUsageStatsPermission()
    }

    fun buyZenMaster() {
        if (_pointsBalance.value >= 300) {
            viewModelScope.launch {
                val newPoints = _pointsBalance.value - 300
                _pointsBalance.value = newPoints
                dataStoreManager.savePoints(newPoints)

                _isZenMasterSuffixUnlocked.value = true
                dataStoreManager.saveZenMasterUnlocked(true)
            }
        }
    }

    fun buyGoldenTheme() {
        if (_pointsBalance.value >= 500) {
            viewModelScope.launch {
                val newPoints = _pointsBalance.value - 500
                _pointsBalance.value = newPoints
                dataStoreManager.savePoints(newPoints)

                _isGoldenThemeUnlocked.value = true
                dataStoreManager.saveGoldenThemeUnlocked(true)
            }
        }
    }

    fun buyCelestialGlow() {
        if (_pointsBalance.value >= 800) {
            viewModelScope.launch {
                val newPoints = _pointsBalance.value - 800
                _pointsBalance.value = newPoints
                dataStoreManager.savePoints(newPoints)

                _isCelestialGlowUnlocked.value = true
                dataStoreManager.saveCelestialGlowUnlocked(true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationHelper.cancelActiveFocusNotification()
        FocusAccessibilityService.isFocusActive = false
    }
}
