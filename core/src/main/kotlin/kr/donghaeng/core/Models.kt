package kr.donghaeng.core

enum class Facility(val label: String) {
    TOILET("화장실"), DIAPER("기저귀 교환대"), NURSING("수유 공간"), FOOD("식사"), ACCESSIBLE("유모차 접근"), PET_ALLOWED("반려동물 동반")
}
enum class CompanionKind(val label: String) { PERSON("사람"), DOG("반려견"), CAT("반려묘"), OTHER_PET("기타 반려동물") }
enum class Evidence { CONFIRMED, ABSENT, UNKNOWN }
data class FamilyMember(
    val id: String, val name: String, val age: String,
    val onboard: Boolean = true, val needs: Set<Facility> = emptySet(),
    val medical: String = "", val contact: String = "", val kind: CompanionKind = CompanionKind.PERSON
)
data class Place(
    val id: String, val name: String, val latitude: Double, val longitude: Double,
    val facilities: Map<Facility, Evidence>, val direction: String,
    val source: String, val checkedAt: String, val hours: String,
    val demo: Boolean = false, val note: String = ""
) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0); require(longitude.isFinite() && longitude in -180.0..180.0) }
}
data class GeoPoint(val lat: Double, val lon: Double) {
    init { require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0) }
}
data class LivePlace(val id: String, val name: String, val point: GeoPoint, val address: String = "", val phone: String = "", val category: String = "", val url: String = "", val offset: Int? = null) {
    fun toPlace() = Place("kakao-$id", name, point.lat, point.lon, emptyMap(), "도로 접근 방향 미확인", "카카오 로컬 검색", "시설 미확인", "운영시간 미확인", note = address)
}
data class DrivingRoute(val points: List<GeoPoint>, val distance: Int, val duration: Int, val priority: String)
object PlaceRequests {
    fun query(text: String, facility: Facility? = null): String = when {
        "동물병원" in text -> "동물병원"
        "졸음쉼터" in text -> "졸음쉼터"
        "휴게소" in text || "기저귀" in text || "수유" in text -> "휴게소"
        "반려" in text || "강아지" in text || "애견" in text -> if ("산책" in text || "공원" in text) "반려견 공원" else "애견동반 카페"
        facility == Facility.TOILET -> "공중화장실"
        facility == Facility.DIAPER || facility == Facility.NURSING -> "휴게소"
        facility == Facility.FOOD -> "음식점"
        "공원" in text || "산책" in text -> "공원"
        "카페" in text || "커피" in text -> "카페"
        "쉬" in text || "휴식" in text -> "휴게소"
        else -> text.replace(Regex("(경로\\s*근처|경로\\s*주변|근처|주변|지금|좀|찾아\\s*줘요?|찾아주세요|검색해\\s*줘요?|검색해주세요|갈\\s*곳)"), " ").trim().take(100).ifBlank { "휴게소" }
    }
    fun isPlaceRequest(text: String) = listOf("찾아", "검색", "갈곳", "갈 곳", "들를", "근처", "휴게소", "졸음쉼터", "카페", "공원", "산책", "애견동반", "동물병원").any { it in text }
}
object Recommendations {
    fun find(places: List<Place>, required: Set<Facility>, preferred: Set<Facility> = emptySet()): List<Place> =
        places.filter { p -> required.all { p.facilities[it] == Evidence.CONFIRMED } }
            .sortedWith(compareByDescending<Place> { p -> preferred.count { p.facilities[it] == Evidence.CONFIRMED } }.thenBy { it.name })
    fun requiredFor(family: List<FamilyMember>): Set<Facility> = family.filter { it.onboard }.flatMap { it.needs }.toSet()
}
object DemoPlaces {
    // Fictional fixtures: never eligible for real navigation or represented as surveyed facilities.
    val all = listOf(
        Place("demo-1", "동행 쉼터 A", 37.0, 127.0,
            mapOf(Facility.TOILET to Evidence.CONFIRMED, Facility.DIAPER to Evidence.CONFIRMED, Facility.FOOD to Evidence.CONFIRMED, Facility.ACCESSIBLE to Evidence.CONFIRMED),
            "시연 경로 · 진행 방향 예시", "가상 시연 데이터", "실제 확인 없음", "운영시간 미확인", true, "시설 배치 예시: 본관 1층 가족 화장실 옆"),
        Place("demo-2", "동행 쉼터 B", 37.01, 127.02,
            mapOf(Facility.TOILET to Evidence.CONFIRMED, Facility.DIAPER to Evidence.CONFIRMED, Facility.NURSING to Evidence.CONFIRMED),
            "시연 경로 · 진행 방향 예시", "가상 시연 데이터", "실제 확인 없음", "운영시간 미확인", true, "시설 배치 예시: 안내 데스크 옆"),
        Place("demo-3", "동행 쉼터 C", 37.02, 127.04,
            mapOf(Facility.TOILET to Evidence.CONFIRMED, Facility.DIAPER to Evidence.UNKNOWN, Facility.FOOD to Evidence.CONFIRMED),
            "접근 방향 미확인", "가상 시연 데이터", "실제 확인 없음", "운영시간 미확인", true)
    )
}
object PhoneNumbers {
    fun clean(value: String): String? {
        val stripped = value.replace(Regex("[\\s()-]"), "")
        return stripped.takeIf { it.matches(Regex("\\+?[0-9]{3,15}")) }
    }
}
object RestClock {
    fun remainingMinutes(lastRest: Long, now: Long, interval: Int): Int =
        (interval - ((now - lastRest).coerceAtLeast(0) / 60_000).toInt()).coerceAtLeast(0)
}
