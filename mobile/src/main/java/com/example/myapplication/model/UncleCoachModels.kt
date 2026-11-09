package com.example.myapplication.model

enum class DrivingState(val label: String) {
    PRE_DRIVE("주차 중 (P)"),
    DRIVING("주행 중 (D)"),
    POST_DRIVE("주차 중 (P) · 주행 완료")
}

enum class PracticeGoal(
    val id: String,
    val title: String,
    val subtitle: String,
    val routeHints: List<String>,
    val uncleTip: String
) {
    MERGE(
        id = "merge",
        title = "고속도로·간선 합류",
        subtitle = "본선 속도에 맞춰 부드럽게 진입하기",
        routeHints = listOf(
            "1.2km 앞 판교IC 톨게이트 (우측 감속 구간) · 하이패스 차로 사전 확인 및 감속 필요",
            "3.8km 앞 청계산 분기점 합류 구간 · 사이드미러 확인 및 방향지시등 점등"
        ),
        uncleTip = "본선 차들 속도에 맞추며 자연스럽게 합류하는 게 핵심이야!"
    ),
    LANE_CHANGE(
        id = "lane_change",
        title = "차선 변경 안정화",
        subtitle = "깜빡이 먼저 켜고 사이드미러 여유 있게 보기",
        routeHints = listOf(
            "800m 앞 왕복 8차선 직선 구간 · 차선 변경 연습 추천 구간",
            "2.5km 앞 교차로 진입 전 좌측 차선 이동 필요 구간"
        ),
        uncleTip = "깜빡이 켜고 하나, 둘, 셋 세고 들어가면 뒤차도 다 양보해 줘. 급하게 꺾지만 마!"
    ),
    TURN(
        id = "turn",
        title = "교차로 좌·우회전",
        subtitle = "유도선 따라 크게 돌고 횡단보도 앞 일시정지",
        routeHints = listOf(
            "500m 앞 시흥사거리 우회전 전용차로 · 횡단보도 보행자 우선 확인",
            "1.9km 앞 비보호 좌회전 구간 · 맞은편 직진 차량 확인 후 진입"
        ),
        uncleTip = "우회전할 땐 횡단보도 보행자부터 꼭 확인하고, 좌회전은 유도선만 잘 따라가면 반은 성공이야."
    ),
    PARKING(
        id = "parking",
        title = "후진·평행 주차",
        subtitle = "사이드미러와 후방 가이드라인 맞춰 천천히 넣기",
        routeHints = listOf(
            "목적지 지하주차장 B2층 여유 공간 넓음 · 기둥 옆 자리 진입 추천"
        ),
        uncleTip = "주차는 열 번 수정해서 넣어도 아무도 뭐라 안 해! 멈추고 핸들 돌리는 것만 기억해."
    )
}

enum class VoiceAlertMode(val label: String) {
    ALL("전체 알림"),
    DANGER_ONLY("위험만 알림"),
    MUTE("음소거")
}

enum class EventType(val label: String, val isPositive: Boolean, val priority: Int) {
    CLOSE_DISTANCE("차간거리 근접", false, 1),
    HARD_BRAKE("급제동 감지", false, 1),
    RAPID_ACCEL("급가속 감지", false, 1),
    MERGE_AHEAD("합류 구간 진입 안내", false, 2),
    GOOD_LANE_CHANGE("안정적인 차선 변경", true, 3),
    GOOD_MERGE("부드러운 본선 합류", true, 3)
}

data class DrivingEvent(
    val id: Long,
    val timestamp: String,
    val locationLabel: String,
    val type: EventType,
    val uncleVoiceMessage: String,
    val speedKmh: Int
)

data class SessionReview(
    val sessionNumber: Int,
    val dateText: String,
    val goal: PracticeGoal,
    val durationMin: Int,
    val distanceKm: Double,
    val hardBrakeCount: Int,
    val closeDistanceCount: Int,
    val smoothLaneChangeCount: Int,
    val smoothMergeCount: Int,
    val stabilityScore: Int,
    val praisePoint: String,
    val improvementTips: List<String>,
    val highlights: List<String>,
    val uncleClosingComment: String
)

data class MentorState(
    val isConnected: Boolean = true,
    val mentorName: String = "아버지",
    val inviteCode: String = "H-8294",
    val isCoRidingMode: Boolean = false,
    val shareEventCount: Boolean = true,
    val shareStabilityScore: Boolean = true,
    val shareRouteHighlights: Boolean = true,
    val mentorAssignedGoal: PracticeGoal = PracticeGoal.MERGE,
    val latestMentorComment: String = "속도 맞춰서 합류 잘했네. 급제동만 조금 일찍 밟는 연습 해보자. 수고했어!",
    val mentorCommentDate: String = "오늘 16:42",
    val sharedToMentor: Boolean = false
)

data class GraduationMission(
    val id: Int,
    val title: String,
    val subtitle: String,
    val isCompleted: Boolean
)

data class GrowthRecord(
    val driverName: String = "지민",
    val daysTogether: Int = 32,
    val totalDistanceKm: Int = 248,
    val totalSessions: Int = 7,
    val graduationProgressPercent: Int = 60,
    val weeklyMissionTitle: String = "이번 주 미션: 고속도로·간선 합류 3회 안정적으로 진입하기",
    val weeklyMissionCurrent: Int = 2,
    val weeklyMissionTarget: Int = 3,
    val missions: List<GraduationMission> = listOf(
        GraduationMission(1, "첫 안전 주행 완료", "안전의 첫걸음", true),
        GraduationMission(2, "규정 속도 지키기", "전 구간 속도 준수", true),
        GraduationMission(3, "부드러운 차선 변경", "주행 안정성 90점 이상", true),
        GraduationMission(4, "고속도로 합류 도전", "본선 흐름에 맞춰 합류하기", false),
        GraduationMission(5, "혼자서 주차 성공", "출발부터 주차까지 스스로", false)
    ),
    val sessionHistory: List<SessionSummaryItem> = listOf(
        SessionSummaryItem(7, "11/09 오늘", "고속도로·간선 합류", 92, "24분 · 12.8km · 급제동 1회 · 규정속도 100%"),
        SessionSummaryItem(6, "11/06 수", "차선 변경 안정화", 88, "19분 · 9.4km · 부드러운 스티어링 94점"),
        SessionSummaryItem(5, "11/03 일", "교차로 좌·우회전", 84, "22분 · 10.2km · 우회전 일시정지 완벽"),
        SessionSummaryItem(4, "10/30 수", "고속도로·간선 합류", 76, "18분 · 8.5km · 급제동 2회 감지")
    ),
    val isGraduated: Boolean = false,
    val isMentorModeActive: Boolean = false
) {
    val completedMissionCount: Int
        get() = missions.count { it.isCompleted }
}

data class SessionSummaryItem(
    val sessionNo: Int,
    val date: String,
    val goalTitle: String,
    val stabilityIndex: Int,
    val summary: String
)
