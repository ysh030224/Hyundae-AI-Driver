package com.example.myapplication.ui

import android.app.Activity
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.audio.UncleVoiceSpeaker
import com.example.myapplication.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Exact Figma PDF Color Palette (Slides 1, 2, 3)
private val FigmaBgDark = Color(0xFF101315)
private val FigmaCardNavy = Color(0xFF152236)
private val FigmaCardDeepNavy = Color(0xFF101D2E)
private val FigmaCardCharcoal = Color(0xFF191D20)
private val FigmaCardElevated = Color(0xFF1F2937)
private val FigmaBorderSubtle = Color(0xFF263449)
private val FigmaBlue = Color(0xFF0052CC)
private val FigmaSkyBlue = Color(0xFF38BDF8)
private val FigmaOrange = Color(0xFFFF6B00)
private val FigmaMintWave = Color(0xFFADE7CE)
private val FigmaEmerald = Color(0xFF10B981)
private val FigmaDanger = Color(0xFFEF4444)
private val FigmaTextCream = Color(0xFFF5F4ED)
private val FigmaTextSecondary = Color(0xFFABB7C7)
private val FigmaTextMuted = Color(0xFF7D8B9E)

enum class AppScreenSection(val label: String) {
    PRE_DRIVE("연습 목표 · 경로 안내"),
    DRIVING("주행 중 AI 음성 코칭"),
    POST_DRIVE("주행 완료 · 안정성 분석"),
    GROWTH("성장 기록장")
}

@Composable
fun UncleCoachApp() {
    val context = LocalContext.current
    val activity = context as? Activity

    val voiceSpeaker = remember(context) { UncleVoiceSpeaker(context) }
    DisposableEffect(voiceSpeaker) {
        onDispose { voiceSpeaker.shutdown() }
    }

    var showSafetySheet by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var currentSection by remember { mutableStateOf(AppScreenSection.PRE_DRIVE) }
    var drivingState by remember { mutableStateOf(DrivingState.PRE_DRIVE) }
    var selectedGoal by remember { mutableStateOf(PracticeGoal.MERGE) }
    var voiceAlertMode by remember { mutableStateOf(VoiceAlertMode.ALL) }

    var currentSpeedKmh by remember { mutableIntStateOf(0) }
    var followingDistanceMeters by remember { mutableIntStateOf(45) }
    var currentGear by remember { mutableStateOf("P") }
    var lastSpokenAlert by remember { mutableStateOf("본선 차들 속도에 맞추며 자연스럽게 합류하는 게 핵심이야!") }
    var lastEventType by remember { mutableStateOf<EventType?>(null) }
    var alertCountMap by remember { mutableStateOf<Map<EventType, Int>>(emptyMap()) }

    val recordedEvents = remember {
        mutableStateListOf(
            DrivingEvent(
                id = 1L,
                timestamp = "16:28",
                locationLabel = "판교IC 진입 전 300m",
                type = EventType.MERGE_AHEAD,
                uncleVoiceMessage = "곧 합류예요. 속도 맞춰서.",
                speedKmh = 62
            ),
            DrivingEvent(
                id = 2L,
                timestamp = "16:34",
                locationLabel = "청계산 분기점 직선 구간",
                type = EventType.HARD_BRAKE,
                uncleVoiceMessage = "괜찮아요. 다음엔 조금 일찍 밟아 봐요.",
                speedKmh = 48
            ),
            DrivingEvent(
                id = 3L,
                timestamp = "16:39",
                locationLabel = "양재대로 차선 변경 구간",
                type = EventType.GOOD_LANE_CHANGE,
                uncleVoiceMessage = "차선 변경 깔끔했어요.",
                speedKmh = 65
            )
        )
    }

    var mentorState by remember { mutableStateOf(MentorState()) }
    var growthRecord by remember { mutableStateOf(GrowthRecord()) }
    var coachChatQuestion by remember { mutableStateOf("") }
    val coachChatHistory = remember {
        mutableStateListOf(
            "연곰 아저씨" to "오늘 판교IC 합류 구간 지날 때 아주 침착했어! 주행하면서 궁금했던 점 있었어?"
        )
    }

    fun speakUncleLine(line: String, eventType: EventType? = null) {
        if (voiceAlertMode == VoiceAlertMode.MUTE) return
        if (voiceAlertMode == VoiceAlertMode.DANGER_ONLY && eventType != null && eventType.priority > 1) return

        if (eventType != null) {
            val count = alertCountMap[eventType] ?: 0
            if (count >= 2) {
                lastSpokenAlert = "동일 알림 2회 제한 적용됨 (${eventType.label})"
                return
            }
            alertCountMap = alertCountMap + (eventType to (count + 1))
        }

        lastSpokenAlert = line
        lastEventType = eventType
        val isAlert = eventType != null && eventType.priority == 1
        voiceSpeaker.speak(line, isAlert = isAlert)
    }

    fun triggerDrivingEvent(type: EventType, line: String, location: String, speed: Int, dist: Int) {
        currentSpeedKmh = speed
        followingDistanceMeters = dist
        val nowStr = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
        recordedEvents.add(
            0,
            DrivingEvent(
                id = System.currentTimeMillis(),
                timestamp = nowStr,
                locationLabel = location,
                type = type,
                uncleVoiceMessage = line,
                speedKmh = speed
            )
        )
        speakUncleLine(line, type)
    }

    fun closeApp() {
        voiceSpeaker.stop()
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        } catch (_: Exception) {}
        activity?.moveTaskToBack(true)
        activity?.finishAndRemoveTask()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = FigmaBgDark
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            val isCompact = maxWidth < 900.dp

            Column(modifier = Modifier.fillMaxSize()) {
                FigmaTopHeaderBar(
                    isCompact = isCompact,
                    currentSection = currentSection,
                    currentGear = currentGear,
                    currentSpeed = currentSpeedKmh,
                    onSelectGear = { gear ->
                        currentGear = gear
                        if (gear == "D") {
                            drivingState = DrivingState.DRIVING
                            currentSection = AppScreenSection.DRIVING
                            if (currentSpeedKmh == 0) currentSpeedKmh = 64
                            alertCountMap = emptyMap()
                            lastEventType = null
                            speakUncleLine("천천히 출발해보자. ${selectedGoal.uncleTip}")
                        } else {
                            currentSpeedKmh = 0
                            if (drivingState == DrivingState.DRIVING) {
                                drivingState = DrivingState.POST_DRIVE
                                currentSection = AppScreenSection.POST_DRIVE
                                speakUncleLine("주행 완료! 오늘도 한 걸음 더 성장했어요.")
                            } else {
                                drivingState = DrivingState.PRE_DRIVE
                                currentSection = AppScreenSection.PRE_DRIVE
                            }
                        }
                    },
                    onTestAudio = {
                        speakUncleLine("안녕! 나는 연곰 아저씨야. 오늘도 나랑 같이 마음 편하게 달려보자!")
                    },
                    onOpenSafety = { showSafetySheet = true },
                    onCloseApp = ::closeApp
                )

                FigmaTabSelectorRow(
                    currentSection = currentSection,
                    onSelectSection = { section ->
                        currentSection = section
                        when (section) {
                            AppScreenSection.PRE_DRIVE -> {
                                drivingState = DrivingState.PRE_DRIVE
                                currentGear = "P"
                                currentSpeedKmh = 0
                            }
                            AppScreenSection.DRIVING -> {
                                drivingState = DrivingState.DRIVING
                                currentGear = "D"
                                if (currentSpeedKmh == 0) currentSpeedKmh = 64
                            }
                            AppScreenSection.POST_DRIVE -> {
                                drivingState = DrivingState.POST_DRIVE
                                currentGear = "P"
                                currentSpeedKmh = 0
                            }
                            AppScreenSection.GROWTH -> {
                                currentGear = "P"
                                currentSpeedKmh = 0
                            }
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompact) 16.dp else 24.dp, vertical = 10.dp)
                ) {
                    when (currentSection) {
                        AppScreenSection.PRE_DRIVE -> FigmaSlide1PreDriveScreen(
                            isCompact = isCompact,
                            selectedGoal = selectedGoal,
                            onSelectGoal = {
                                selectedGoal = it
                                speakUncleLine(it.uncleTip)
                            },
                            mentorState = mentorState,
                            onUpdateMentorState = { mentorState = it },
                            onSpeak = { speakUncleLine(it) },
                            onStartDriving = {
                                currentGear = "D"
                                drivingState = DrivingState.DRIVING
                                currentSection = AppScreenSection.DRIVING
                                currentSpeedKmh = 62
                                alertCountMap = emptyMap()
                                lastEventType = null
                                speakUncleLine("연습 주행을 시작합니다. ${selectedGoal.uncleTip}")
                            }
                        )
                        AppScreenSection.DRIVING -> FigmaSlide2DrivingWaveScreen(
                            isCompact = isCompact,
                            selectedGoal = selectedGoal,
                            voiceAlertMode = voiceAlertMode,
                            onChangeVoiceMode = { voiceAlertMode = it },
                            currentSpeedKmh = currentSpeedKmh,
                            followingDistanceMeters = followingDistanceMeters,
                            lastSpokenAlert = lastSpokenAlert,
                            lastEventType = lastEventType,
                            mentorState = mentorState,
                            onTriggerEvent = ::triggerDrivingEvent,
                            onRepeatVoice = { speakUncleLine(lastSpokenAlert, lastEventType) },
                            onFinishDriving = {
                                currentGear = "P"
                                currentSpeedKmh = 0
                                drivingState = DrivingState.POST_DRIVE
                                currentSection = AppScreenSection.POST_DRIVE
                                speakUncleLine("주행 완료! 오늘도 한 걸음 더 성장했어요.")
                            }
                        )
                        AppScreenSection.POST_DRIVE -> FigmaSlide3PostDriveReviewScreen(
                            isCompact = isCompact,
                            selectedGoal = selectedGoal,
                            recordedEvents = recordedEvents,
                            mentorState = mentorState,
                            onUpdateMentorState = { mentorState = it },
                            onOpenInviteModal = { showInviteDialog = true },
                            onSpeak = { speakUncleLine(it) },
                            onFinishReview = {
                                currentSection = AppScreenSection.GROWTH
                                speakUncleLine("오늘 주행 기록이 성장 기록장에 안전하게 저장되었어요!")
                            },
                            coachChatQuestion = coachChatQuestion,
                            onChangeCoachQuestion = { coachChatQuestion = it },
                            coachChatHistory = coachChatHistory
                        )
                        AppScreenSection.GROWTH -> FigmaGrowthAndMentorScreen(
                            growthRecord = growthRecord,
                            mentorState = mentorState,
                            onUpdateGrowthRecord = { growthRecord = it },
                            onUpdateMentorState = { mentorState = it },
                            onOpenInviteModal = { showInviteDialog = true },
                            onSpeak = { speakUncleLine(it) }
                        )
                    }
                }
            }
        }

        if (showInviteDialog) {
            AlertDialog(
                onDismissRequest = { showInviteDialog = false },
                containerColor = FigmaCardCharcoal,
                titleContentColor = FigmaTextCream,
                textContentColor = FigmaTextSecondary,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        YeongomMascotAvatar(
                            drawableRes = R.drawable.mascot_cheer,
                            sizeDp = 36
                        )
                        Text("멘토 초대하기", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "가족이나 운전 선배에게 초대 코드를 전달해 1:1 멘토로 연결하세요. 실시간 GPS 위치는 공유되지 않으며, 정차 후 내가 선택한 주행 요약만 공유됩니다.",
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        Surface(
                            color = FigmaCardNavy,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, FigmaBlue)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("초대 코드", color = FigmaTextSecondary, fontSize = 11.sp)
                                    Text(
                                        text = mentorState.inviteCode,
                                        color = FigmaTextCream,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Surface(
                                    color = FigmaBlue,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.clickable {
                                        speakUncleLine("초대 코드 ${mentorState.inviteCode} 가 복사되었어요.")
                                        showInviteDialog = false
                                    }
                                ) {
                                    Text(
                                        text = "복사",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showInviteDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("닫기", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showSafetySheet) {
            AlertDialog(
                onDismissRequest = { showSafetySheet = false },
                containerColor = FigmaCardCharcoal,
                titleContentColor = FigmaTextCream,
                textContentColor = FigmaTextSecondary,
                title = {
                    Text("주행 안전 원칙 및 운전 책임 고지", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("• 운전자의 판단 우선: 본 앱은 초보운전자의 연습을 돕는 보조 수단이며, 모든 실제 주행 판단과 책임은 운전자 본인에게 있습니다.")
                        Text("• 주행 중 화면 조작 차단 (Set up while parked): 기어가 D(주행)에 놓이면 복잡한 텍스트를 숨기고 연곰 아저씨의 짧은 음성 파형 코칭만 작동합니다.")
                        Text("• 실시간 조작 지시 배제: '지금 핸들을 꺾으세요' 같은 위험한 지시는 하지 않으며, 구간 사전 예고와 차간거리 주의만 안내합니다.")
                        Text("• 프라이버시 보호: 가족 멘토에게도 실시간 위치는 전송되지 않으며, 정차 후 요약 노트만 공유됩니다.")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSafetySheet = false },
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("확인", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun FigmaTopHeaderBar(
    isCompact: Boolean,
    currentSection: AppScreenSection,
    currentGear: String,
    currentSpeed: Int,
    onSelectGear: (String) -> Unit,
    onTestAudio: () -> Unit,
    onOpenSafety: () -> Unit,
    onCloseApp: () -> Unit
) {
    Surface(
        color = Color(0xFF14191F),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 14.dp else 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                YeongomMascotAvatar(
                    drawableRes = R.drawable.mascot_closeup,
                    sizeDp = if (isCompact) 36 else 42
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (currentSection == AppScreenSection.POST_DRIVE) "주행 완료" else "나의 AI 연수 아저씨",
                            color = FigmaTextCream,
                            fontSize = if (isCompact) 16.sp else 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = if (currentGear == "D") FigmaEmerald.copy(alpha = 0.2f) else FigmaCardNavy,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                if (currentGear == "D") FigmaEmerald else FigmaSkyBlue.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (currentGear == "D") FigmaEmerald else FigmaSkyBlue)
                                )
                                Text(
                                    text = if (currentGear == "D") "주행 중 (D) · ${currentSpeed}km/h" else "주차 중 (P) 16:42",
                                    color = FigmaTextCream,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (!isCompact) {
                        Text(
                            text = if (currentSection == AppScreenSection.POST_DRIVE) {
                                "오늘도 한 걸음 더 성장했어요! · 오늘의 주행 · 24분 · 12.8km"
                            } else {
                                "연곰 아저씨 AI 보이스 코칭 · Set up while parked. Drive at your pace."
                            },
                            color = FigmaTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Gear P / D Switch
                Surface(
                    color = FigmaCardNavy,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, FigmaBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf("P", "D").forEach { gear ->
                            val active = currentGear == gear
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            active && gear == "D" -> FigmaEmerald
                                            active -> FigmaBlue
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onSelectGear(gear) }
                                    .padding(horizontal = 11.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = gear,
                                    color = if (active) Color.White else FigmaTextSecondary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = FigmaOrange.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, FigmaOrange.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { onTestAudio() }
                ) {
                    Text(
                        text = if (isCompact) "🔊 음성" else "🔊 연곰 음성 테스트",
                        color = FigmaOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }

                if (!isCompact) {
                    Surface(
                        color = FigmaCardNavy,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onOpenSafety() }
                    ) {
                        Text(
                            text = "안전수칙",
                            color = FigmaTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                Surface(
                    color = FigmaDanger.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, FigmaDanger.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onCloseApp() }
                ) {
                    Text(
                        text = "✕ 닫기",
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FigmaTabSelectorRow(
    currentSection: AppScreenSection,
    onSelectSection: (AppScreenSection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppScreenSection.entries.forEach { section ->
            val selected = currentSection == section
            val bg by animateColorAsState(
                targetValue = if (selected) FigmaBlue else FigmaCardNavy,
                label = "tabBg"
            )
            Surface(
                color = bg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) FigmaSkyBlue.copy(alpha = 0.6f) else FigmaBorderSubtle
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectSection(section) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = section.label,
                        color = if (selected) FigmaTextCream else FigmaTextSecondary,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * =========================================================================
 * SLIDE 1: 출발 전 화면 (Figma Slide 1 Design)
 * =========================================================================
 */
@Composable
private fun FigmaSlide1PreDriveScreen(
    isCompact: Boolean,
    selectedGoal: PracticeGoal,
    onSelectGoal: (PracticeGoal) -> Unit,
    mentorState: MentorState,
    onUpdateMentorState: (MentorState) -> Unit,
    onSpeak: (String) -> Unit,
    onStartDriving: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Mission Card (Figma Slide 1 Top Card)
        Surface(
            color = FigmaCardNavy,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, FigmaBlue.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isCompact) 18.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = FigmaOrange.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, FigmaOrange)
                    ) {
                        Text(
                            text = "★ 오늘의 추천 미션 · ${mentorState.mentorName} 추천",
                            color = FigmaOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (mentorState.isCoRidingMode) "동승 코칭 모드" else "단독 연습 모드",
                            color = FigmaTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = mentorState.isCoRidingMode,
                            onCheckedChange = { onUpdateMentorState(mentorState.copy(isCoRidingMode = it)) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "오늘의 목표: ${selectedGoal.title}",
                            color = FigmaTextCream,
                            fontSize = if (isCompact) 21.sp else 25.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "예상 15분 · AI 코칭 주행 · ${selectedGoal.subtitle}",
                            color = FigmaTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onStartDriving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FigmaBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "연습 주행 시작하기 →",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }

                // Goal Selector Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PracticeGoal.entries.forEach { goal ->
                        val active = selectedGoal == goal
                        Surface(
                            color = if (active) FigmaBlue.copy(alpha = 0.35f) else FigmaCardDeepNavy,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (active) FigmaSkyBlue else FigmaBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectGoal(goal) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = goal.title,
                                    color = if (active) FigmaTextCream else FigmaTextSecondary,
                                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Yeongom Uncle's One-Line Advice Card (Figma Slide 1 Middle Mascot Card)
        Surface(
            color = FigmaCardDeepNavy,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, FigmaOrange.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSpeak(selectedGoal.uncleTip) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Mascot Image #3 (Guide with clipboard) or #1 (Cheer)
                YeongomMascotCardImage(
                    drawableRes = R.drawable.mascot_guide,
                    sizeDp = if (isCompact) 88 else 104
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = FigmaOrange,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "연곰 아저씨의 한마디",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "🔊 탭하여 목소리 듣기",
                            color = FigmaSkyBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "\"${selectedGoal.uncleTip}\"",
                        color = FigmaTextCream,
                        fontSize = if (isCompact) 16.sp else 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 24.sp
                    )

                    Text(
                        text = "${mentorState.mentorName} 메모: \"${mentorState.latestMentorComment}\"",
                        color = FigmaTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Route Caution Preview Section (Figma Slide 1 Bottom Section)
        Surface(
            color = FigmaCardNavy,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, FigmaBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "주행 경로 위 주의 구간 사전 안내",
                        color = FigmaTextCream,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Surface(
                        color = FigmaEmerald.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, FigmaEmerald.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "✓ 경로 분석 완료",
                            color = FigmaEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                RouteCautionItemCard(
                    index = 1,
                    title = if (selectedGoal == PracticeGoal.MERGE) {
                        "1.2km 앞 판교IC 톨게이트 (우측 감속 구간)"
                    } else {
                        selectedGoal.routeHints.firstOrNull() ?: "주의 구간 1"
                    },
                    subtitle = if (selectedGoal == PracticeGoal.MERGE) {
                        "하이패스 차로 사전 확인 및 감속 필요"
                    } else {
                        "진입 전 사이드미러 및 차간거리 사전 확보"
                    },
                    distanceBadge = "800 m",
                    accentColor = FigmaOrange,
                    onClick = {
                        onSpeak("1.2킬로미터 앞 우측 감속 구간이야. 미리 차로를 확인해 두자.")
                    }
                )

                RouteCautionItemCard(
                    index = 2,
                    title = if (selectedGoal == PracticeGoal.MERGE) {
                        "3.8km 앞 청계산 분기점 합류 구간"
                    } else {
                        selectedGoal.routeHints.getOrNull(1) ?: "목적지 부근 연습 구간"
                    },
                    subtitle = "사이드미러 확인 및 방향지시등 점등",
                    distanceBadge = "2.6 km",
                    accentColor = FigmaSkyBlue,
                    onClick = {
                        onSpeak("청계산 분기점 합류 구간에서는 깜빡이를 미리 켜고 본선 속도에 맞춰 들어가자.")
                    }
                )
            }
        }

        // Bottom Safety Tagline from Figma Slide 1
        Text(
            text = "Set up while parked. Drive at your pace.",
            color = FigmaTextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun RouteCautionItemCard(
    index: Int,
    title: String,
    subtitle: String,
    distanceBadge: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = FigmaCardDeepNavy,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, FigmaBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.2f),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, accentColor),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$index",
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    color = FigmaTextCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    color = FigmaTextSecondary,
                    fontSize = 12.sp
                )
            }

            Surface(
                color = FigmaCardNavy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = distanceBadge,
                    color = FigmaTextCream,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * =========================================================================
 * SLIDE 2: 주행 중 화면 (Figma Slide 2 Center Mascot + Mint Acoustic Waves)
 * =========================================================================
 */
@Composable
private fun FigmaSlide2DrivingWaveScreen(
    isCompact: Boolean,
    selectedGoal: PracticeGoal,
    voiceAlertMode: VoiceAlertMode,
    onChangeVoiceMode: (VoiceAlertMode) -> Unit,
    currentSpeedKmh: Int,
    followingDistanceMeters: Int,
    lastSpokenAlert: String,
    lastEventType: EventType?,
    mentorState: MentorState,
    onTriggerEvent: (EventType, String, String, Int, Int) -> Unit,
    onRepeatVoice: () -> Unit,
    onFinishDriving: () -> Unit
) {
    // Dynamic Mascot Pose Transformation based on Driving Context!
    val currentMascotRes = when (lastEventType) {
        EventType.CLOSE_DISTANCE, EventType.HARD_BRAKE, EventType.RAPID_ACCEL -> R.drawable.mascot_caution
        EventType.GOOD_LANE_CHANGE, EventType.GOOD_MERGE -> R.drawable.mascot_praise
        EventType.MERGE_AHEAD -> R.drawable.mascot_cheer
        null -> R.drawable.mascot_closeup
    }

    val waveColor = when (lastEventType) {
        EventType.CLOSE_DISTANCE, EventType.HARD_BRAKE -> Color(0xFFFCA5A5)
        EventType.GOOD_LANE_CHANGE, EventType.GOOD_MERGE -> FigmaMintWave
        else -> FigmaMintWave
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wavePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Centerpiece Card reproducing Figma Slide 2 (#121212 background + #ADE7CE concentric sound wave arcs)
        Surface(
            color = Color(0xFF121212),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, FigmaMintWave.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isCompact) 18.dp else 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Status Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF1A2421),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, FigmaMintWave.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(FigmaMintWave)
                            )
                            Text(
                                text = if (mentorState.isCoRidingMode) {
                                    "동승 코칭 모드 · 화면 조작 차단 (음성만 안내)"
                                } else {
                                    "AI 보이스 코칭 활성 · 주행 중 화면 조작 차단"
                                },
                                color = FigmaMintWave,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = FigmaCardNavy,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onRepeatVoice() }
                    ) {
                        Text(
                            text = "🔊 다시 듣기",
                            color = FigmaTextCream,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Exact Figma Slide 2 Acoustic Wave + Center Yeongom Mascot
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 230.dp else 260.dp)
                        .clickable { onRepeatVoice() }
                ) {
                    // Left & Right Concentric Mint Soundwave Arcs (#ADE7CE from Slide 2 PDF)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isCompact) 220.dp else 250.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radii = listOf(
                            size.height * 0.42f,
                            size.height * 0.56f,
                            size.height * 0.72f
                        )
                        val alphas = listOf(0.85f, 0.50f, 0.25f)
                        val strokeWidths = listOf(5.dp.toPx(), 4.dp.toPx(), 3.dp.toPx())

                        radii.forEachIndexed { idx, r ->
                            val arcSize = Size(r * 2f, r * 2f)
                            val topLeft = Offset(cx - r, cy - r)
                            // Right sound wave arc (-38 deg to +38 deg)
                            drawArc(
                                color = waveColor.copy(alpha = alphas[idx]),
                                startAngle = -38f,
                                sweepAngle = 76f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidths[idx], cap = StrokeCap.Round)
                            )
                            // Left sound wave arc (142 deg to 218 deg)
                            drawArc(
                                color = waveColor.copy(alpha = alphas[idx]),
                                startAngle = 142f,
                                sweepAngle = 76f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidths[idx], cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Soft radial glow behind mascot
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 168.dp else 192.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        waveColor.copy(alpha = 0.28f),
                                        Color(0xFF152236).copy(alpha = 0.75f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Center Mascot with gentle floating posture animation
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        border = BorderStroke(3.dp, waveColor),
                        modifier = Modifier
                            .size(if (isCompact) 144.dp else 166.dp)
                            .graphicsLayer {
                                translationY = bobOffset
                            }
                    ) {
                        Image(
                            painter = painterResource(id = currentMascotRes),
                            contentDescription = "연곰 아저씨 보이스 코치",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                // Short Voice Line (<= 20 chars rule during driving)
                Text(
                    text = "\"$lastSpokenAlert\"",
                    color = FigmaTextCream,
                    fontSize = if (isCompact) 20.sp else 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                // Speed & Following Distance Pill Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentSpeedKmh} km/h",
                        color = FigmaSkyBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "차간거리 ${followingDistanceMeters}m",
                        color = if (followingDistanceMeters < 20) FigmaDanger else FigmaMintWave,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "목표: ${selectedGoal.title}",
                        color = FigmaTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Simulation Trigger Bar (Lets user test the posture transformations + Korean TTS voice)
        Surface(
            color = FigmaCardNavy,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, FigmaBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "주행 상황별 연곰 아저씨 표정·포즈 변화 & 음성 코칭 시연",
                        color = FigmaTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VoiceAlertMode.entries.forEach { mode ->
                            val selected = voiceAlertMode == mode
                            Surface(
                                color = if (selected) FigmaBlue else FigmaCardDeepNavy,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { onChangeVoiceMode(mode) }
                            ) {
                                Text(
                                    text = mode.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrivingPoseTriggerButton(
                        title = "합류 구간 예고",
                        quote = "\"곧 합류예요. 속도 맞춰서.\"",
                        poseLabel = "응원 포즈",
                        accent = FigmaSkyBlue,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onTriggerEvent(
                                EventType.MERGE_AHEAD,
                                "곧 합류예요. 속도 맞춰서.",
                                "판교IC 진입 200m 앞",
                                64,
                                38
                            )
                        }
                    )
                    DrivingPoseTriggerButton(
                        title = "차간거리 주의",
                        quote = "\"앞차랑 좀 가까워요. 천천히.\"",
                        poseLabel = "정지·주의 포즈",
                        accent = FigmaDanger,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onTriggerEvent(
                                EventType.CLOSE_DISTANCE,
                                "앞차랑 좀 가까워요. 천천히.",
                                "판교대로 직선 구간",
                                78,
                                14
                            )
                        }
                    )
                    DrivingPoseTriggerButton(
                        title = "급제동 다독임",
                        quote = "\"괜찮아요. 다음엔 조금 일찍.\"",
                        poseLabel = "다독임 포즈",
                        accent = FigmaOrange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onTriggerEvent(
                                EventType.HARD_BRAKE,
                                "괜찮아요. 다음엔 조금 일찍 밟아 봐요.",
                                "시흥사거리 앞",
                                32,
                                18
                            )
                        }
                    )
                    DrivingPoseTriggerButton(
                        title = "차선 변경 성공",
                        quote = "\"차선 변경 깔끔했어요!\"",
                        poseLabel = "쌍따봉 칭찬",
                        accent = FigmaEmerald,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onTriggerEvent(
                                EventType.GOOD_LANE_CHANGE,
                                "차선 변경 깔끔했어요.",
                                "양재대로 3차로",
                                68,
                                42
                            )
                        }
                    )
                }
            }
        }

        Button(
            onClick = onFinishDriving,
            colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "주차 완료 (기어 P 전환) · 주행 안정성 분석 및 아버지 피드백 확인 →",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun DrivingPoseTriggerButton(
    title: String,
    quote: String,
    poseLabel: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = FigmaCardDeepNavy,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = accent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = poseLabel,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = title,
                color = FigmaTextCream,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = quote,
                color = FigmaTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * =========================================================================
 * SLIDE 3: 주행 완료 & 안정성 분석 화면 (Figma Slide 3 Design)
 * =========================================================================
 */
@Composable
private fun FigmaSlide3PostDriveReviewScreen(
    isCompact: Boolean,
    selectedGoal: PracticeGoal,
    recordedEvents: List<DrivingEvent>,
    mentorState: MentorState,
    onUpdateMentorState: (MentorState) -> Unit,
    onOpenInviteModal: () -> Unit,
    onSpeak: (String) -> Unit,
    onFinishReview: () -> Unit,
    coachChatQuestion: String,
    onChangeCoachQuestion: (String) -> Unit,
    coachChatHistory: MutableList<Pair<String, String>>
) {
    val hardBrakeCount = recordedEvents.count { it.type == EventType.HARD_BRAKE }.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Driving Stability Analysis Card (Exact Figma Slide 3 Metrics: 82, 94, 100)
        Surface(
            color = FigmaCardCharcoal,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, FigmaBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "주행 안정성 분석",
                            color = FigmaTextCream,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "차량 API 기반 실시간 데이터 집계",
                            color = FigmaTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        color = FigmaCardElevated,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "100점 만점 (높을수록 안정적)",
                            color = FigmaSkyBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                FigmaStabilityMetricBar(
                    title = "급제동 제어",
                    description = "급제동 ${hardBrakeCount}회 감지 (사전 브레이크 권장)",
                    score = 82,
                    barColor = FigmaOrange
                )

                FigmaStabilityMetricBar(
                    title = "조향/차선 변경",
                    description = "부드럽고 안정적인 스티어링 조작",
                    score = 94,
                    barColor = FigmaSkyBlue
                )

                FigmaStabilityMetricBar(
                    title = "속도 준수율",
                    description = "전 구간 규정 속도 완벽 준수",
                    score = 100,
                    barColor = FigmaEmerald
                )
            }
        }

        // 2. Mentor Card ("아버지 / Your mentor / 오늘 합류 아주 매끄러웠다!") + Mascot Praise
        Surface(
            color = FigmaCardCharcoal,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, FigmaOrange.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        YeongomMascotCardImage(
                            drawableRes = R.drawable.mascot_praise,
                            sizeDp = 64
                        )
                        Column {
                            Text(
                                text = mentorState.mentorName,
                                color = FigmaTextCream,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Your mentor · 초대 코드 ${mentorState.inviteCode}",
                                color = FigmaOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenInviteModal,
                            border = BorderStroke(1.dp, FigmaSkyBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "+ 멘토 초대",
                                color = FigmaSkyBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                onSpeak("아버지 멘토 피드백입니다. 오늘 합류 아주 매끄러웠다! ${mentorState.latestMentorComment}")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🔊 음성 듣기", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Surface(
                    color = FigmaCardNavy,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, FigmaBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "오늘 합류 아주 매끄러웠다!",
                            color = FigmaOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "“${mentorState.latestMentorComment}”",
                            color = FigmaTextCream,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }

                // Bottom Status Row from Figma Slide 3: "성장 기록장에 저장됨" + "리뷰 종료"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = FigmaEmerald.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✓", color = FigmaEmerald, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }
                        }
                        Text(
                            text = "성장 기록장에 저장됨",
                            color = FigmaTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onUpdateMentorState(mentorState.copy(sharedToMentor = true))
                                onSpeak("${mentorState.mentorName}에게 오늘 주행 분석 카드를 전송했어요!")
                            },
                            border = BorderStroke(1.dp, FigmaEmerald),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (mentorState.sharedToMentor) "✓ 멘토 공유됨" else "${mentorState.mentorName}께 결과 공유",
                                color = FigmaEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = onFinishReview,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FigmaBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "리뷰 종료",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Parked Q&A with Yeongom Uncle (Preserving interactive AI Q&A feature)
        Surface(
            color = FigmaCardCharcoal,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, FigmaBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "정차 중 연곰 아저씨에게 질문하기 (주차 P 전용)",
                    color = FigmaTextCream,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )

                coachChatHistory.forEach { (speaker, msg) ->
                    val isUncle = speaker == "연곰 아저씨"
                    Surface(
                        color = if (isUncle) FigmaCardNavy else FigmaCardElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = speaker,
                                color = if (isUncle) FigmaOrange else FigmaSkyBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = msg,
                                color = FigmaTextCream,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = coachChatQuestion,
                        onValueChange = onChangeCoachQuestion,
                        placeholder = {
                            Text(
                                "예: 합류할 때 본선 차가 안 비켜주면 어떡해요?",
                                color = FigmaTextMuted,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FigmaTextCream,
                            unfocusedTextColor = FigmaTextCream,
                            focusedBorderColor = FigmaSkyBlue,
                            unfocusedBorderColor = FigmaBorderSubtle
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val q = if (coachChatQuestion.isBlank()) {
                                "합류할 때 언제 들어가야 할지 타이밍이 어려워요"
                            } else {
                                coachChatQuestion
                            }
                            coachChatHistory.add("나 (초보운전자)" to q)
                            val reply = "가속차로 끝에서 멈추면 더 힘들어져! 가속차로 초입부터 본선 속도만큼 쭉 올린 뒤, 사이드미러에 뒷차 헤드라이트 두 개가 온전히 보일 때 부드럽게 들어가면 돼."
                            coachChatHistory.add("연곰 아저씨" to reply)
                            onChangeCoachQuestion("")
                            onSpeak(reply)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("질문", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FigmaStabilityMetricBar(
    title: String,
    description: String,
    score: Int,
    barColor: Color
) {
    Surface(
        color = FigmaCardNavy,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, FigmaBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = FigmaTextCream,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = description,
                        color = FigmaTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "$score",
                    color = barColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp
                )
            }

            LinearProgressIndicator(
                progress = { score / 100f },
                color = barColor,
                trackColor = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * =========================================================================
 * TAB 4: 성장 기록장 & 멘토 프라이버시 설정
 * =========================================================================
 */
@Composable
private fun FigmaGrowthAndMentorScreen(
    growthRecord: GrowthRecord,
    mentorState: MentorState,
    onUpdateGrowthRecord: (GrowthRecord) -> Unit,
    onUpdateMentorState: (MentorState) -> Unit,
    onOpenInviteModal: () -> Unit,
    onSpeak: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            color = FigmaCardNavy,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, FigmaBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        YeongomMascotCardImage(
                            drawableRes = if (growthRecord.isGraduated) R.drawable.mascot_praise else R.drawable.mascot_cheer,
                            sizeDp = 70
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (growthRecord.isGraduated) {
                                    "초보운전 졸업 완료! (멘토 전환 가능)"
                                } else {
                                    "초보운전 졸업 진행도 ${growthRecord.graduationProgressPercent}%"
                                },
                                color = FigmaTextCream,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = growthRecord.weeklyMissionTitle,
                                color = FigmaSkyBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val nextGrad = !growthRecord.isGraduated
                            onUpdateGrowthRecord(
                                growthRecord.copy(
                                    isGraduated = nextGrad,
                                    graduationProgressPercent = if (nextGrad) 100 else 74
                                )
                            )
                            if (nextGrad) {
                                onSpeak("초보운전 졸업을 진심으로 축하해! 이제 누군가의 든든한 연수 멘토가 될 수 있어.")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (growthRecord.isGraduated) FigmaOrange else FigmaEmerald,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (growthRecord.isGraduated) "연습 과정 보기" else "졸업식 시연",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { growthRecord.graduationProgressPercent / 100f },
                    color = if (growthRecord.isGraduated) FigmaEmerald else FigmaSkyBlue,
                    trackColor = FigmaCardDeepNavy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
            }
        }

        // Session History
        Surface(
            color = FigmaCardCharcoal,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, FigmaBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "누적 주행 성장 기록장",
                    color = FigmaTextCream,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                growthRecord.sessionHistory.forEach { item ->
                    Surface(
                        color = FigmaCardNavy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.sessionNo}회차 · ${item.date} (${item.goalTitle})",
                                    color = FigmaTextCream,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = item.summary,
                                    color = FigmaTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Surface(
                                color = FigmaBlue.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, FigmaSkyBlue)
                            ) {
                                Text(
                                    text = "${item.stabilityIndex}점",
                                    color = FigmaSkyBlue,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mentor Invite & Privacy Controls (Figma Slide 3 Bottom Card)
        Surface(
            color = FigmaCardCharcoal,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, FigmaBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "멘토 초대 및 공유 범위 설정",
                            color = FigmaTextCream,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "초대 코드: ${mentorState.inviteCode} · 현재 연결: ${if (mentorState.isConnected) mentorState.mentorName else "해제됨"}",
                            color = FigmaOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = onOpenInviteModal,
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("초대 코드 보기", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("급제동·차선변경 안정성 지표 공유", color = FigmaTextSecondary, fontSize = 13.sp)
                    Switch(
                        checked = mentorState.shareEventCount,
                        onCheckedChange = { onUpdateMentorState(mentorState.copy(shareEventCount = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("정차 후 주요 연습 구간 요약 공유 (실시간 위치 제외)", color = FigmaTextSecondary, fontSize = 13.sp)
                    Switch(
                        checked = mentorState.shareRouteHighlights,
                        onCheckedChange = { onUpdateMentorState(mentorState.copy(shareRouteHighlights = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun YeongomMascotAvatar(
    @DrawableRes drawableRes: Int,
    sizeDp: Int
) {
    Surface(
        color = Color.White,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, FigmaSkyBlue),
        modifier = Modifier.size(sizeDp.dp)
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = "연곰 아저씨",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
private fun YeongomMascotCardImage(
    @DrawableRes drawableRes: Int,
    sizeDp: Int
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, FigmaSkyBlue.copy(alpha = 0.6f)),
        modifier = Modifier.size(sizeDp.dp)
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = "연곰 아저씨 캐릭터",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        )
    }
}
