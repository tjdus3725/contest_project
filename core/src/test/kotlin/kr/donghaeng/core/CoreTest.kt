package kr.donghaeng.core

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun petProfilesAndUnknownPetFacilitiesAreNotConfirmed() {
        val pet = FamilyMember("dog", "초코", "3살", needs = setOf(Facility.PET_ALLOWED), kind = CompanionKind.DOG)
        assertEquals(setOf(Facility.PET_ALLOWED), Recommendations.requiredFor(listOf(pet)))
        assertTrue(Recommendations.find(DemoPlaces.all, setOf(Facility.PET_ALLOWED)).isEmpty())
        assertTrue(LivePlace("1", "장소", GeoPoint(37.0, 127.0)).toPlace().facilities.isEmpty())
    }
    @Test fun petAndFacilityRequestsProduceSearchTermsWithoutInventedPlaces() {
        assertEquals("반려견 공원", PlaceRequests.query("강아지 산책할 공원 찾아줘"))
        assertEquals("휴게소", PlaceRequests.query("기저귀 갈 곳", Facility.DIAPER))
        assertEquals("스타벅스", PlaceRequests.query("경로 근처 스타벅스 찾아줘"))
        assertTrue(PlaceRequests.isPlaceRequest("경로 근처 동물병원 찾아줘"))
    }
    private val ai = SituationAssistant()
    @Test fun unknownFacilityNeverPassesRequiredFilter() {
        val matches = Recommendations.find(DemoPlaces.all, setOf(Facility.DIAPER))
        assertEquals(listOf("demo-1", "demo-2"), matches.map { it.id })
        assertFalse(matches.any { it.id == "demo-3" })
    }
    @Test fun allRequiredFacilitiesMustMatch() {
        assertEquals(listOf("demo-2"), Recommendations.find(DemoPlaces.all, setOf(Facility.DIAPER, Facility.NURSING)).map { it.id })
        assertTrue(Recommendations.find(DemoPlaces.all, setOf(Facility.NURSING, Facility.FOOD)).isEmpty())
    }
    @Test fun onlyOnboardFamilyRequirementsAreApplied() {
        val family = listOf(FamilyMember("1", "아이", "유아", true, setOf(Facility.DIAPER)), FamilyMember("2", "가족", "성인", false, setOf(Facility.ACCESSIBLE)))
        assertEquals(setOf(Facility.DIAPER), Recommendations.requiredFor(family))
    }
    @Test fun redFlagsOverrideRoutineFacilityRequests() {
        listOf("아이가 의식이 없어요 화장실 찾아줘", "숨을 못 쉬어요", "아이가 쓰러졌어요", "반응이 없어요", "출혈이 있어요").forEach {
            val result = ai.assess(it); assertTrue(it, result.urgent); assertEquals(Situation.MEDICAL, result.situation); assertNull(result.facility)
        }
    }
    @Test fun fireAndFloodOverrideRestIntent() {
        assertEquals(Situation.FIRE, ai.assess("화장실 가려는데 차에서 연기가 나요").situation)
        assertTrue(ai.assess("차가 물에 잠겨요").urgent)
    }
    @Test fun facilityRequestRoutesToSearch() {
        assertEquals(Facility.DIAPER, ai.assess("기저귀 교환할 곳 찾아줘").facility)
        assertEquals(Situation.REST, ai.assess("아이 화장실 갈 곳 찾아줘").situation)
    }
    @Test fun medicalComplaintDoesNotBecomeRestaurantSearch() {
        assertEquals(Situation.MEDICAL, ai.assess("아이가 배가 아프다고 해요 밥 먹을 곳").situation)
    }
    @Test fun unfamiliarOrEmptyInputAbstains() {
        listOf("", "음악 틀어 줘", "asdfghjkl", "내일 발표 자료").forEach { assertEquals(it, Situation.UNKNOWN, ai.assess(it).situation) }
    }
    @Test fun modelGeneralizesVehicleComplaint() {
        assertEquals(Situation.BREAKDOWN, ai.assess("차량 타이어 펑크가 났습니다").situation)
    }
    @Test fun conservativeNegationDoesNotDeclareSafe() {
        assertTrue(ai.assess("의식이 없는 건 아닌데 이상해요").urgent)
    }
    @Test fun restClockHandlesBackwardClockAndOverdue() {
        assertEquals(90, RestClock.remainingMinutes(10000, 0, 90))
        assertEquals(0, RestClock.remainingMinutes(0, 120 * 60000L, 90))
        assertEquals(30, RestClock.remainingMinutes(0, 60 * 60000L, 90))
    }
    @Test fun dialerRejectsUssdAndUriInjection() {
        assertNull(PhoneNumbers.clean("*123#")); assertNull(PhoneNumbers.clean("119?x=1"))
        assertEquals("01012345678", PhoneNumbers.clean("010-1234-5678"))
        assertEquals("119", PhoneNumbers.clean("119"))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinatesRejected() {
        DemoPlaces.all.first().copy(latitude = Double.NaN)
    }
}
