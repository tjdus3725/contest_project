package kr.donghaeng.core

import kotlin.math.sqrt

enum class Situation(val label: String) { REST("휴식·시설"), MEDICAL("동승자 이상"), BREAKDOWN("차량 고장"), ACCIDENT("교통사고"), FIRE("연기·화재"), FLOOD("침수"), UNKNOWN("상황 확인 필요") }
data class Assessment(val situation: Situation, val urgent: Boolean, val headline: String, val steps: List<String>, val facility: Facility? = null)

/** Offline character-bigram nearest-centroid intent model + conservative safety rules.
 * Similarity is NEVER a medical risk probability. Prototype, not clinically validated.
 * Free-form generation is deliberately excluded from emergency instructions.
 */
class SituationAssistant {
    private val corpus = mapOf(
        Situation.REST to listOf("아이 화장실 갈 곳 찾아줘", "기저귀 교환할 곳 필요해", "잠깐 쉬고 싶어", "수유할 곳 찾아줘", "밥 먹을 식당 찾아줘", "휴게소에서 쉬고 싶어요"),
        Situation.MEDICAL to listOf("아이가 아파요", "어지럽고 토할 것 같아요", "아이가 멀미를 해요", "배가 아프다고 해요", "몸이 안 좋아요", "갑자기 숨쉬기 힘들어요"),
        Situation.BREAKDOWN to listOf("차가 고장 났어요", "시동이 안 걸려요", "타이어가 펑크 났어요", "차가 멈췄어요", "배터리가 방전됐어요", "견인차가 필요해요"),
        Situation.ACCIDENT to listOf("교통사고가 났어요", "뒤에서 차가 부딪혔어요", "추돌 사고 발생", "차량 충돌했어요"),
        Situation.FIRE to listOf("차에서 연기가 나요", "차량에 불이 났어요", "타는 냄새가 나요", "엔진에서 연기가 보여요"),
        Situation.FLOOD to listOf("차가 물에 잠겨요", "도로가 침수됐어요", "차 안으로 물이 들어와요", "물이 차올라요")
    )
    private fun features(text: String): Map<String, Double> = text.lowercase().replace(Regex("[^가-힣a-z0-9]"), "").windowed(2).groupingBy { it }.eachCount().mapValues { it.value.toDouble() }
    private val centroids = corpus.mapValues { (_, examples) ->
        val out = mutableMapOf<String, Double>()
        examples.forEach { s -> features(s).forEach { (k, v) -> out[k] = (out[k] ?: 0.0) + v } }; out
    }
    private fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        val norm = sqrt(a.values.sumOf { it * it } * b.values.sumOf { it * it })
        return if (norm == 0.0) 0.0 else a.entries.sumOf { it.value * (b[it.key] ?: 0.0) } / norm
    }
    fun assess(raw: String): Assessment {
        val text = raw.take(500).replace(" ", "").lowercase()
        val redFlags = listOf("의식", "반응이없", "반응없", "숨", "호흡", "질식", "경련", "피가", "출혈", "쓰러", "못깨", "안움직", "가슴통증", "가슴이아", "unconscious", "notbreathing", "choking")
        // Conservative matching intentionally does not attempt to infer negation or declare safety.
        if (redFlags.any { text.contains(it) }) return guide(Situation.MEDICAL, true)
        if (listOf("연기", "화재", "불이", "타는냄새").any { text.contains(it) }) return guide(Situation.FIRE)
        if (listOf("침수", "잠겨", "잠기", "물에", "물이가", "물이차", "물차").any { text.contains(it) }) return guide(Situation.FLOOD)
        if (listOf("사고", "충돌", "추돌", "부딪").any { text.contains(it) }) return guide(Situation.ACCIDENT)
        if (listOf("아파", "아프", "토해", "토하", "멀미", "어지", "이상해").any { text.contains(it) }) return guide(Situation.MEDICAL)
        val ranked = centroids.map { (k, v) -> k to cosine(features(raw), v) }.sortedByDescending { it.second }
        val intent = if (ranked[0].second >= .22 && ranked[0].second - ranked[1].second >= .035) ranked[0].first else Situation.UNKNOWN
        val facility = when {
            "기저귀" in text -> Facility.DIAPER
            "수유" in text -> Facility.NURSING
            "화장실" in text -> Facility.TOILET
            "밥" in text || "식사" in text || "식당" in text -> Facility.FOOD
            else -> null
        }
        return guide(if (facility != null) Situation.REST else intent).copy(facility = facility)
    }
    fun guide(s: Situation, urgent: Boolean = false): Assessment = when (s) {
        Situation.REST -> Assessment(s, false, "필요한 시설을 함께 찾아볼게요", listOf("필수 시설이 확인된 후보만 표시합니다.", "진행 방향과 실제 경로는 내비게이션에서 확인하세요."))
        Situation.MEDICAL -> Assessment(s, urgent, if (urgent) "즉시 119에 도움을 요청하세요" else "동승자의 상태를 확인해 주세요", listOf("의식이나 호흡에 이상이 있거나 판단하기 어려우면 119에 도움을 요청하세요.", "현재 위치와 관찰한 상태를 전달하세요. AI 답변을 기다리지 않아도 됩니다.", "119 연결 후에는 상담원의 안내를 우선 따르세요."))
        Situation.BREAKDOWN -> Assessment(s, false, "주변 위험을 확인하고 도움을 요청하세요", listOf("다친 사람, 연기 또는 주변 교통 위험이 있는지 확인하세요.", "인명 위험이 있으면 119 또는 112에 도움을 요청하세요.", "위험 상황이 아니라면 등록한 보험사의 긴급출동에 연락하세요."))
        Situation.ACCIDENT -> Assessment(s, true, "사람의 안전과 긴급 연락이 우선이에요", listOf("부상자나 즉각적인 위험이 있으면 119에 도움을 요청하세요.", "경찰 도움이 필요하면 112로 연락하세요.", "위치·인원·관찰한 상황을 전달하고 현장 지시를 따르세요."))
        Situation.FIRE, Situation.FLOOD -> Assessment(s, true, "119에 상황과 위치를 알려주세요", listOf("본인과 동승자의 안전을 우선하고 긴급기관에 도움을 요청하세요.", "연기·물·주변 교통 등 관찰한 위험을 전달하세요.", "앱의 추가 질문을 기다리지 말고 긴급기관의 지시를 따르세요."))
        Situation.UNKNOWN -> Assessment(s, false, "상황을 확실하게 이해하지 못했어요", listOf("아래 상황 버튼을 선택하거나 짧게 다시 말씀해 주세요.", "위험하거나 판단하기 어렵다면 119 또는 112로 바로 연락할 수 있어요."))
    }
}
