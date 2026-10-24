package kr.donghaeng.car

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.donghaeng.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DonghaengModel(app: Application) : AndroidViewModel(app) {
    private val store = PrivateStore(app)
    private val assistant = SituationAssistant()
    var tab by mutableIntStateOf(0)
    var family by mutableStateOf(listOf<FamilyMember>())
    var places by mutableStateOf(listOf<Place>())
    var sampleMode by mutableStateOf(false)
    var mapServer by mutableStateOf("http://localhost:8787")
    var mapToken by mutableStateOf("")
    var consentMap by mutableStateOf(false)
    val planner = RoutePlanner(viewModelScope) {
        check(consentMap) { "MY에서 지도·장소·경로 서비스의 위치·검색어 처리에 동의해 주세요" }
        MapApi(mapServer, mapToken)
    }
    var requested by mutableStateOf(setOf<Facility>())
    var selected by mutableStateOf<Place?>(null)
    var assessment by mutableStateOf<Assessment?>(null)
    var petAssessment by mutableStateOf(false)
    var situationText by mutableStateOf("")
    var navStatus by mutableStateOf("내비게이션 연결 확인 중")
    var notice by mutableStateOf("")
    var locationText by mutableStateOf("현재 위치 미확인 · GPS 권한과 수신 상태를 확인하세요")
    var uxRestricted by mutableStateOf(true)
    var uxStatus by mutableStateOf("차량 주행 상태 확인 중")
    var demoDriving by mutableStateOf(false)
    var tripActive by mutableStateOf(false)
    var lastRest by mutableLongStateOf(0L)
    var interval by mutableIntStateOf(90)
    var vehicle by mutableStateOf("")
    var insurer by mutableStateOf("")
    var insurancePhone by mutableStateOf("")
    var checklist by mutableStateOf(listOf("물", "기저귀", "여벌 옷", "개인 준비물"))
    var checked by mutableStateOf(setOf<String>())
    var consentSpeech by mutableStateOf(false)
    var storageBlocked by mutableStateOf(false)
    val nav = PlatformNavigation(app) { navStatus = it }
    val editingAllowed get() = !uxRestricted && !demoDriving
    val required get() = Recommendations.requiredFor(family) + requested
    val candidates get() = Recommendations.find(places, required).take(3)
    init {
        try {
            val root = store.read()
            val members = root.optJSONArray("family") ?: JSONArray()
            family = (0 until members.length()).map { i ->
                val p = members.getJSONObject(i)
                FamilyMember(p.getString("id"), p.getString("name"), p.optString("age"), p.optBoolean("onboard", true),
                    Facility.entries.filter { p.optJSONArray("needs")?.toString()?.contains("\"${it.name}\"") == true }.toSet(), p.optString("medical"), p.optString("contact"),
                    runCatching { CompanionKind.valueOf(p.optString("kind", "PERSON")) }.getOrDefault(CompanionKind.PERSON))
            }
            val list = root.optJSONArray("places") ?: JSONArray()
            places = (0 until list.length()).map { i -> decodePlace(list.getJSONObject(i)) }
            vehicle = root.optString("vehicle"); insurer = root.optString("insurer"); insurancePhone = root.optString("insurancePhone")
            interval = root.optInt("interval", 90).coerceIn(15, 240)
            consentSpeech = root.optBoolean("consentSpeech")
            mapServer = root.optString("mapServer", "http://localhost:8787"); mapToken = root.optString("mapToken"); consentMap = root.optBoolean("consentMap")
            root.optJSONArray("checklist")?.let { a -> checklist = (0 until a.length()).map { a.getString(it) } }
        } catch (e: Exception) { storageBlocked = true; notice = "저장 정보를 읽지 못했습니다. 기존 파일을 보존했습니다. MY에서 초기화하거나 앱을 다시 실행하세요." }
        nav.connect()
    }
    fun save() {
        if (storageBlocked) { notice = "저장소 복구 또는 초기화가 필요합니다. 변경 내용은 저장되지 않았습니다."; return }
        val members = JSONArray().apply { family.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("age", p.age).put("onboard", p.onboard)
            .put("needs", JSONArray(p.needs.map { it.name })).put("medical", p.medical).put("contact", p.contact).put("kind", p.kind.name)) } }
        val data = JSONObject().put("family", members).put("places", JSONArray().apply { places.forEach { put(encodePlace(it)) } })
            .put("vehicle", vehicle).put("insurer", insurer).put("insurancePhone", insurancePhone).put("interval", interval)
            .put("consentSpeech", consentSpeech).put("checklist", JSONArray(checklist))
            .put("mapServer", mapServer).put("mapToken", mapToken).put("consentMap", consentMap)
        try { store.write(data); notice = "기기에 암호화해 저장했습니다" }
        catch (e: Exception) { notice = "저장 실패 · 입력은 현재 화면에만 남아 있습니다" }
    }
    fun query(text: String) {
        situationText = text.take(500)
        assessment = assistant.assess(text)
        val result = assessment!!
        val pet = listOf("강아지", "고양이", "반려견", "반려묘", "반려동물").any { it in text } && listOf("아이", "사람", "운전자", "보호자", "동승자").none { it in text }
        petAssessment = false
        if (pet && (result.situation == Situation.MEDICAL || result.urgent) && result.situation !in listOf(Situation.FIRE, Situation.FLOOD, Situation.ACCIDENT)) {
            petAssessment = true
            assessment = Assessment(Situation.MEDICAL, result.urgent, "반려동물의 이상은 동물병원에 문의하세요", listOf("반려동물에 사람용 응급 안내나 약을 적용하지 마세요.", "가까운 동물병원에 먼저 연락해 진료 가능 여부를 확인하세요. 차량·사람도 위험하면 긴급기관에 도움을 요청하세요.")); tab = 2
        } else if (result.situation == Situation.REST || (!result.urgent && result.situation == Situation.UNKNOWN && PlaceRequests.isPlaceRequest(text))) {
            requested = setOfNotNull(result.facility); tab = 1
            planner.query = PlaceRequests.query(text, result.facility)
            if (planner.baseRoute != null && consentMap) planner.searchAlong()
        } else tab = 2
    }
    fun guide(s: Situation) { petAssessment = false; assessment = assistant.guide(s); situationText = "사용자 선택: ${s.label}"; tab = 2 }
    fun choose(place: Place) {
        selected = place
        if (place.demo) navStatus = "시연: ${place.name} 선택 · 실제 내비게이션 미전송" else nav.addStop(place)
        tab = 0
    }
    fun addFamily(name: String, age: String, needs: Set<Facility>, medical: String, contact: String, id: String? = null, kind: CompanionKind = CompanionKind.PERSON) {
        val member = FamilyMember(id ?: UUID.randomUUID().toString(), name.trim(), age.trim(), family.find { it.id == id }?.onboard ?: true, needs, medical.trim(), contact.trim(), kind)
        family = family.filterNot { it.id == member.id } + member; save()
    }
    fun startTrip() { tripActive = true; lastRest = System.currentTimeMillis(); checked = emptySet() }
    fun deleteAll() {
        store.clear(); family = emptyList(); places = emptyList(); vehicle = ""; insurer = ""; insurancePhone = ""
        checklist = listOf("물", "기저귀", "여벌 옷", "개인 준비물"); interval = 90; consentSpeech = false
        selected = null; requested = emptySet(); assessment = null; petAssessment = false; situationText = ""; tripActive = false; checked = emptySet()
        locationText = "현재 위치 미확인"; storageBlocked = false; notice = "이 기기의 저장 정보를 삭제했습니다"
        planner.reset(); mapToken = ""; mapServer = "http://localhost:8787"; consentMap = false
    }
    override fun onCleared() { nav.close(); super.onCleared() }
    private fun encodePlace(p: Place) = JSONObject().put("id", p.id).put("name", p.name).put("lat", p.latitude).put("lon", p.longitude)
        .put("facilities", JSONObject().apply { p.facilities.forEach { (k,v) -> put(k.name, v.name) } }).put("direction", p.direction)
        .put("source", p.source).put("checkedAt", p.checkedAt).put("hours", p.hours).put("note", p.note)
    private fun decodePlace(o: JSONObject) = Place(o.getString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"),
        Facility.entries.associateWith { Evidence.valueOf(o.optJSONObject("facilities")?.optString(it.name, "UNKNOWN") ?: "UNKNOWN") },
        o.optString("direction"), o.optString("source"), o.optString("checkedAt"), o.optString("hours"), note = o.optString("note"))
}
