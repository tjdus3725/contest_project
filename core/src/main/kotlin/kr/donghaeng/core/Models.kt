package kr.donghaeng.core

enum class Facility(val label: String) {
    TOILET("화장실"), DIAPER("기저귀 교환대"), NURSING("수유 공간"), FOOD("식사"), ACCESSIBLE("유모차 접근")
}
enum class Evidence { CONFIRMED, ABSENT, UNKNOWN }
data class FamilyMember(
    val id: String, val name: String, val age: String,
    val onboard: Boolean = true, val needs: Set<Facility> = emptySet(),
    val medical: String = "", val contact: String = ""
)
data class Place(
    val id: String, val name: String, val latitude: Double, val longitude: Double,
    val facilities: Map<Facility, Evidence>, val direction: String,
    val source: String, val checkedAt: String, val hours: String,
    val demo: Boolean = false, val note: String = ""
) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0); require(longitude.isFinite() && longitude in -180.0..180.0) }
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
