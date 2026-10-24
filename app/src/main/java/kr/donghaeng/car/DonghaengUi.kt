@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package kr.donghaeng.car

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.donghaeng.core.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID

private val Background = Color(0xFF101B23)
private val Panel = Color(0xFF1B2A34)
private val Mint = Color(0xFF8EE1C0)
private val Muted = Color(0xFFAABEC9)
private val Red = Color(0xFFFF897E)
private val palette = darkColorScheme(primary = Mint, onPrimary = Color(0xFF103C2D), background = Background,
    surface = Panel, onSurface = Color(0xFFF2F6F5), secondary = Color(0xFFDCC89C), error = Red)

@Composable
fun DonghaengApp(m: DonghaengModel, voice: () -> Unit, speak: (String) -> Unit, dial: (String) -> Unit, locate: () -> Unit) {
    MaterialTheme(colorScheme = palette) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(m.notice) { if (m.notice.isNotBlank()) { snackbar.showSnackbar(m.notice); m.notice = "" } }
        Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Surface(color = Color(0xFF14232C), shadowElevation = 10.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val names = listOf("홈", "장소 찾기", "SOS", "가족", "MY")
                    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Place, Icons.Rounded.Sos, Icons.Rounded.Groups, Icons.Rounded.Settings)
                    names.forEachIndexed { i, name ->
                        if (i == 2) {
                            Button(onClick = { m.tab = 2 }, modifier = Modifier.weight(1f).height(76.dp).padding(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC4E46), contentColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icons[i], "긴급 도움", Modifier.size(30.dp)); Text(name, fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            TextButton(onClick = { m.tab = i }, modifier = Modifier.weight(1f).height(68.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = if (m.tab == i) Mint else Muted)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(icons[i], null, Modifier.size(26.dp)); Text(name, fontSize = 16.sp, fontWeight = if (m.tab == i) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Diversity1, null, tint = Mint, modifier = Modifier.size(28.dp))
                    Text("  동행", fontWeight = FontWeight.Bold, fontSize = 25.sp)
                    Spacer(Modifier.width(16.dp))
                    Text(if (BuildConfig.FLAVOR == "demo") "DEMO · 전화 모의 실행" else "PLEOS · 차량용 시제품", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                    IconButton(onClick = voice, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Mic, "음성 요청", tint = Mint) }
                }
                if (!m.editingAllowed) Text("주행 중이거나 차량 상태 미확인 · 상세 입력은 정차 후 이용하세요", color = Color(0xFFE9CA8D), modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp), fontSize = 14.sp)
                when (m.tab) {
                    0 -> Home(m, voice)
                    1 -> Places(m, voice, locate)
                    2 -> Emergency(m, voice, speak, dial, locate)
                    3 -> Family(m)
                    else -> Settings(m)
                }
            }
        }
    }
}

@Composable private fun Page(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column { Text(title, fontSize = 29.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
        content()
    }
}
@Composable private fun BoxCard(title: String, modifier: Modifier = Modifier, accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, color = if (accent) Color(0xFF193D36) else Panel, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, color = if (accent) Mint else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            content()
        }
    }
}
@Composable private fun Action(label: String, icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(icon, null, Modifier.size(23.dp)); Spacer(Modifier.width(10.dp)); Text(label, fontSize = 16.sp)
    }
}
@Composable private fun Split(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1.25f)) { left() }; Column(Modifier.weight(1f)) { right() }
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { left(); right() }
    }
}
@Composable private fun Home(m: DonghaengModel, voice: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(m.tripActive) { while (m.tripActive) { now = System.currentTimeMillis(); delay(1000) } }
    val remaining = RestClock.remainingMinutes(m.lastRest, now, m.interval)
    Page("함께 가는 길, 조금 더 편안하게", "가족의 필요를 챙기고 다음 쉼을 준비해요.") {
        Split(left = {
            BoxCard(if (m.tripActive) "이번 이동" else "출발 준비", Modifier.fillMaxWidth(), true) {
                Text(if (m.family.none { it.onboard }) "함께 갈 가족을 선택해 주세요" else m.family.filter { it.onboard }.joinToString(" · ") { it.name }, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(m.required.joinToString(" · ") { it.label }.ifEmpty { "필요한 시설을 선택하면 추천에 반영해요" }, color = Muted)
                RouteArt()
                Text(m.selected?.let { "다음 정차 후보  ${it.name}" } ?: "아직 선택한 정차 장소가 없어요", fontSize = 19.sp)
                Text(m.navStatus, color = Mint, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Action(if (m.tripActive) "이동 종료" else "이동 시작", Icons.Rounded.DirectionsCar) {
                        if (m.tripActive) m.tripActive = false else m.startTrip()
                    }
                    Action("동승자 선택", Icons.Rounded.Groups, m.editingAllowed) { m.tab = 3 }
                }
            }
        }, right = {
            BoxCard("다음 휴식", Modifier.fillMaxWidth()) {
                Text(if (!m.tripActive) "준비되면 출발해요" else if (remaining == 0) "휴식할 시간이에요" else "${remaining}분 뒤", fontSize = 32.sp, color = Mint, fontWeight = FontWeight.Bold)
                Text("설정한 ${m.interval}분 간격 기준 · 건강 상태 자동 감지 아님", color = Muted)
                Action("쉬어갈 곳 찾기", Icons.Rounded.LocalCafe) { m.requested = emptySet(); m.tab = 1 }
                if (m.tripActive) Action("휴식 완료 기록", Icons.Rounded.Check) { m.lastRest = System.currentTimeMillis() }
            }
        })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("화장실", Icons.Rounded.Wc) { m.requested = setOf(Facility.TOILET); m.planner.query = "공중화장실"; m.tab = 1 }
            Action("기저귀 교환", Icons.Rounded.ChildCare) { m.requested = setOf(Facility.DIAPER); m.planner.query = "휴게소"; m.tab = 1 }
            Action("식사", Icons.Rounded.Restaurant) { m.requested = setOf(Facility.FOOD); m.planner.query = "음식점"; m.tab = 1 }
            Action("말로 요청하기", Icons.Rounded.Mic, onClick = voice)
        }
        if (m.editingAllowed) BoxCard("출발·도착 체크리스트", Modifier.fillMaxWidth()) {
            Text("직접 확인한 항목을 표시하세요. 주차·탑승 상태를 자동 감지하지 않습니다.", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                m.checklist.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item in m.checked, { m.checked = if (it) m.checked + item else m.checked - item }); Text(item)
                } }
            }
        }
        Action("상황별 안전 안내 미리 보기", Icons.Rounded.HealthAndSafety) { m.tab = 2 }
    }
}
@Composable private fun RouteArt() {
    Canvas(Modifier.fillMaxWidth().height(82.dp)) {
        val path = Path().apply { moveTo(16f, size.height * .7f); cubicTo(size.width * .3f, size.height * .7f, size.width * .4f, size.height * .25f, size.width - 18f, size.height * .25f) }
        drawPath(path, Color(0xFF356454), style = Stroke(14f))
        drawPath(path, Mint, style = Stroke(3f))
        drawCircle(Mint, 9f, Offset(16f, size.height * .7f))
        drawCircle(Mint, 9f, Offset(size.width - 18f, size.height * .25f))
    }
}
@Composable private fun Places(m: DonghaengModel, voice: () -> Unit, locate: () -> Unit) {
    var detail by remember { mutableStateOf<Place?>(null) }
    var add by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Place?>(null) }
    Page("우리 가족에게 맞는 다음 쉼", "실제 장소 검색 · 카카오 자동차 경로 · 반려동물 동반 조건") {
        Action("음성 요청", Icons.Rounded.Mic, onClick = voice)
        LivePlacesPanel(m, locate)
        Text("직접 확인해 저장한 시설", fontSize = 22.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Facility.entries.forEach { f ->
                FilterChip(selected = f in m.required, onClick = {
                    if (f in Recommendations.requiredFor(m.family)) m.notice = "가족 프로필의 필수 조건입니다. 가족 탭에서 변경하세요."
                    else m.requested = if (f in m.requested) m.requested - f else m.requested + f
                }, label = { Text(f.label) }, modifier = Modifier.heightIn(min = 52.dp))
            }
        }
        if (m.candidates.isEmpty()) BoxCard("조건에 맞는 장소가 없어요", Modifier.fillMaxWidth()) {
            Text("필수 시설이 미확인인 장소는 추천에서 제외됩니다. 장소를 직접 등록하거나 필요한 조건을 확인해 주세요.", color = Muted)
        }
        m.candidates.forEachIndexed { index, place ->
            BoxCard("${index + 1}. ${place.name}", Modifier.fillMaxWidth(), index == 0) {
                Text(place.facilities.filterValues { it == Evidence.CONFIRMED }.keys.joinToString(" · ") { it.label }, color = Mint)
                Text("${place.direction}  |  ${place.hours}", color = Muted)
                Text("출처: ${place.source} · 확인: ${place.checkedAt}", fontSize = 13.sp, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Action(if (place.demo) "이 장소 선택 · 시연" else "경유지 연결 요청", Icons.Rounded.Navigation) {
                        if (place.demo) m.choose(place) else confirm = place
                    }
                    Action("시설 상세", Icons.Rounded.Info, m.editingAllowed) { detail = place }
                }
            }
        }
        Action("확인한 장소 직접 등록", Icons.Rounded.AddLocationAlt, m.editingAllowed) { add = true }
    }
    if (!m.editingAllowed) { LaunchedEffect(Unit) { add = false; detail = null } }
    detail?.let { p -> AlertDialog(onDismissRequest = { detail = null }, title = { Text(p.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Facility.entries.forEach { f -> Text("${f.label}: ${when(p.facilities[f]) { Evidence.CONFIRMED -> "확인됨"; Evidence.ABSENT -> "없음"; else -> "미확인" }}") }
            Text(p.note.ifBlank { "상세 위치 정보 없음" }); Text("운영: ${p.hours}\n출처: ${p.source}\n확인: ${p.checkedAt}\n접근: ${p.direction}")
            Text("추가 주행·체류 시간: 미제공")
        }
    }, confirmButton = { TextButton(onClick = { detail = null }) { Text("닫기") } }, dismissButton = {
        if (!p.demo) TextButton(onClick = {
            m.places = m.places.map { if (it.id == p.id) it.copy(facilities = Facility.entries.associateWith { Evidence.UNKNOWN }, note = "사용자 오류 제보 · 재확인 필요") else it }; m.save(); detail = null
        }) { Text("정보 오류 표시") }
    }) }
    confirm?.let { p -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("차량 경유지를 변경할까요?") }, text = {
        Text("${p.name}\n차량 내비게이션에 목적지를 먼저 설정하세요. 첫 번째 경유지에 추가를 요청합니다. 기존 경유지가 있다면 차량 화면에서 변경 내용을 확인하세요. 접근 방향과 운영 정보는 직접 확인해야 합니다.")
    }, confirmButton = { TextButton(onClick = { m.choose(p); confirm = null }) { Text("연결 요청") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } }) }
    if (add && m.editingAllowed) PlaceEditor(onDismiss = { add = false }, onSave = { m.places = m.places + it; m.sampleMode = false; m.save(); add = false })
}

@Composable private fun Emergency(m: DonghaengModel, voice: () -> Unit, speak: (String) -> Unit, dial: (String) -> Unit, locate: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var step by remember(m.assessment) { mutableIntStateOf(0) }
    var memberId by remember { mutableStateOf<String?>(null) }
    Page("지금, 도움이 필요한가요?", "긴급 연락은 질문에 답하지 않고 바로 이용할 수 있어요.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dial("119") }, modifier = Modifier.height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC4E46), contentColor = Color.White)) { Icon(Icons.Rounded.Call, null); Text(" 119 구조·구급", fontSize = 20.sp) }
            Action("112 경찰", Icons.Rounded.Call) { dial("112") }
            Action("보험사 긴급출동", Icons.Rounded.CarRepair) { dial(m.insurancePhone) }
            Action("상황 말하기", Icons.Rounded.Mic, onClick = voice)
        }
        if (m.editingAllowed) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it.take(500) }, label = { Text("예: 아이가 아파요 / 차에서 연기가 나요") }, modifier = Modifier.weight(1f), maxLines = 2)
            Action("상황 확인", Icons.Rounded.AutoAwesome, input.isNotBlank()) { m.query(input) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Situation.MEDICAL, Situation.ACCIDENT, Situation.BREAKDOWN, Situation.FIRE, Situation.FLOOD).forEach { s ->
                FilterChip(m.assessment?.situation == s, { m.guide(s) }, label = { Text(s.label) }, modifier = Modifier.heightIn(min = 54.dp))
            }
        }
        m.assessment?.let { result ->
            BoxCard(result.headline, Modifier.fillMaxWidth(), !result.urgent) {
                if (result.urgent) Text("긴급 도움 우선 · AI 안내를 기다리지 마세요", color = Red, fontWeight = FontWeight.Bold)
                Text("${step + 1} / ${result.steps.size}", color = Mint)
                Text(result.steps[step], fontSize = 23.sp, lineHeight = 34.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Action("안내 듣기", Icons.Rounded.VolumeUp) { speak(result.steps[step]) }
                    Action("다음 안내", enabled = step < result.steps.lastIndex) { step++ }
                    if (step > 0) Action("처음부터") { step = 0 }
                }
                if (m.petAssessment) {
                    Action("동물병원 찾기", Icons.Rounded.Pets) { m.planner.query = "동물병원"; m.tab = 1; if (m.planner.baseRoute != null && m.consentMap) m.planner.searchAlong() }
                } else if (result.situation == Situation.MEDICAL && !result.urgent) {
                    Text("의식·호흡에 이상이 있거나 판단하기 어렵나요?", fontWeight = FontWeight.Bold)
                    Action("예 / 모르겠어요 · 119", Icons.Rounded.Call) { dial("119") }
                }
            }
        }
        Split(left = {
            BoxCard("위치와 신고용 요약", Modifier.fillMaxWidth()) {
                Text(m.locationText, fontSize = 17.sp)
                Action("위치 새로 확인", Icons.Rounded.MyLocation, onClick = locate)
                Text("${m.situationText.ifBlank { "상황 미입력" }}\n등록: 사람 ${m.family.count { it.onboard && it.kind == CompanionKind.PERSON }}명 / 반려동물 ${m.family.count { it.onboard && it.kind != CompanionKind.PERSON }}마리 · 실제 인원은 직접 확인", color = Muted)
                Text("앱은 신고 접수·구조대 출동 여부를 확인하지 않습니다.", color = Muted, fontSize = 13.sp)
            }
        }, right = {
            BoxCard("동승자 응급 참고 정보", Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.family.filter { it.onboard }.forEach { p -> FilterChip(memberId == p.id, { memberId = p.id }, label = { Text(p.name) }, modifier = Modifier.heightIn(min = 52.dp)) }
                }
                val person = m.family.find { it.id == memberId }
                Text(person?.let { "${it.name} · ${it.age}\n${it.medical.ifBlank { "등록한 응급 정보 없음" }}" } ?: "해당 동승자를 선택해 주세요", color = Muted)
                if (person != null) Action("등록 보호자 전화", Icons.Rounded.Call) { dial(person.contact) }
                Text("사용자가 등록한 정보 · 의료기관 확인 자료 아님", fontSize = 13.sp, color = Muted)
            }
        })
        Text("온디바이스 문장 분류 모델 + 위험 신호 규칙 · 의료 진단 아님\n오프라인 기본 안내 v1 · 2026-10-24 자료 참조 / 전문가 검수 전 시제품\n참고: 소방청 119 구급신고 요령. 실제 상황에서는 긴급기관 지시를 우선하세요.", color = Muted, fontSize = 13.sp)
    }
}

@Composable private fun Family(m: DonghaengModel) {
    var editing by remember { mutableStateOf<FamilyMember?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FamilyMember?>(null) }
    Page("함께 타는 가족", "이번 이동의 동승자를 선택하면 필요한 시설을 자동으로 적용해요.") {
        if (m.family.isEmpty()) BoxCard("첫 동승자를 등록해 주세요", Modifier.fillMaxWidth()) { Text("별칭과 필요한 시설만으로 시작할 수 있어요. 의료 정보는 선택 사항입니다.", color = Muted) }
        m.family.forEach { p -> BoxCard(p.name, Modifier.fillMaxWidth(), p.onboard) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("${p.kind.label} · ${p.age.ifBlank { "연령대 미입력" }}"); Text(p.needs.joinToString(" · ") { it.label }.ifEmpty { "필수 시설 없음" }, color = Muted) }
                Text("이번 이동 동승"); Switch(p.onboard, { onboard -> m.family = m.family.map { if (it.id == p.id) it.copy(onboard = onboard) else it }; m.save() }, enabled = m.editingAllowed)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("정보 수정", Icons.Rounded.Edit, m.editingAllowed) { editing = p }
                Action("삭제", Icons.Rounded.DeleteOutline, m.editingAllowed) { deleting = p }
            }
        } }
        Action("동승자 추가", Icons.Rounded.PersonAdd, m.editingAllowed) { adding = true }
        Text("가족 정보는 이 기기에 암호화해 저장합니다. 외부 AI 서버로 전송하지 않습니다.", color = Muted)
    }
    if ((adding || editing != null) && m.editingAllowed) FamilyEditor(editing, { adding = false; editing = null }) { name, age, needs, medical, contact, kind ->
        m.addFamily(name, age, needs, medical, contact, editing?.id, kind); adding = false; editing = null
    }
    deleting?.let { p -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("${p.name} 정보를 삭제할까요?") }, text = { Text("이 기기에 등록한 동승자 정보가 삭제됩니다.") },
        confirmButton = { TextButton(onClick = { if (m.editingAllowed) { m.family = m.family.filterNot { it.id == p.id }; m.save() }; deleting = null }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }) }
}

@Composable private fun Settings(m: DonghaengModel) {
    var reset by remember { mutableStateOf(false) }
    var vehicle by remember { mutableStateOf(m.vehicle) }
    var insurer by remember { mutableStateOf(m.insurer) }
    var phone by remember { mutableStateOf(m.insurancePhone) }
    var checklist by remember { mutableStateOf(m.checklist.joinToString(", ")) }
    var mapServer by remember { mutableStateOf(m.mapServer) }
    var mapToken by remember { mutableStateOf(m.mapToken) }
    Page("MY · 이동 설정", "차량과 연락처, 데이터 사용 범위를 관리해요.") {
        BoxCard("연결 상태", Modifier.fillMaxWidth()) {
            Text(m.navStatus, color = Mint); Text(m.uxStatus, color = Muted)
            Text("Pleos NaviHelper 3.0.5 · 위치/음성은 Android 제공 서비스 사용\n주행 중 동작 범위는 차량 정책과 앱 심사에 따라 제한됩니다.", color = Muted)
            if (BuildConfig.FLAVOR == "demo") Row(verticalAlignment = Alignment.CenterVertically) {
                Text("주행 제한 시연"); Switch(m.demoDriving, { m.demoDriving = it })
            }
        }
        BoxCard("차량·보험", Modifier.fillMaxWidth()) {
            Entry(vehicle, { vehicle = it }, "차량 별칭 / 차량번호", m.editingAllowed)
            Entry(insurer, { insurer = it }, "보험사", m.editingAllowed)
            Entry(phone, { phone = it }, "긴급출동 전화번호", m.editingAllowed)
            Action("차량 정보 저장", Icons.Rounded.Save, m.editingAllowed) {
                if (phone.isNotBlank() && PhoneNumbers.clean(phone) == null) m.notice = "전화번호 형식을 확인해 주세요"
                else { m.vehicle = vehicle.trim(); m.insurer = insurer.trim(); m.insurancePhone = phone.trim(); m.save() }
            }
        }
        BoxCard("지도 서비스", Modifier.fillMaxWidth()) {
            Entry(mapServer, { mapServer = it }, "지도 서버 주소 (개발: http://localhost:8787)", m.editingAllowed)
            OutlinedTextField(mapToken, { mapToken = it.take(500) }, label = { Text("서버 연결 토큰 (선택 / 카카오 키 아님)") }, enabled = m.editingAllowed,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("위치·검색어의 지도 서비스 처리 동의", modifier = Modifier.weight(1f))
                Switch(m.consentMap, { m.consentMap = it; m.planner.cancelForConfiguration(); m.save() }, enabled = m.editingAllowed)
            }
            Text("동의하면 지도 표시를 위해 카카오 웹 SDK가 로드되며, 출발·도착·경유 좌표와 검색어를 설정한 서버 및 카카오로 전송합니다. 가족 의료 정보는 전송하지 않습니다. REST API 키는 앱에 입력하지 말고 서버에 설정하세요.", color = Muted)
            Action("지도 연결 설정 저장", Icons.Rounded.Save, m.editingAllowed) {
                try { MapApi(mapServer, mapToken); m.mapServer = mapServer.trim().trimEnd('/'); m.mapToken = mapToken.trim(); m.planner.cancelForConfiguration(); m.save() }
                catch (e: Exception) { m.notice = e.message ?: "서버 주소를 확인하세요" }
            }
        }
        BoxCard("휴식·준비물", Modifier.fillMaxWidth()) {
            Text("휴식 간격 ${m.interval}분 · 앱을 사용하는 동안 안내")
            Slider(m.interval.toFloat(), { m.interval = it.toInt() }, valueRange = 15f..240f, steps = 14, enabled = m.editingAllowed, onValueChangeFinished = { m.save() })
            Entry(checklist, { checklist = it }, "준비물 (쉼표로 구분)", m.editingAllowed)
            Action("준비물 저장", Icons.Rounded.Check, m.editingAllowed) { m.checklist = checklist.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().take(20); m.save() }
        }
        BoxCard("음성·개인정보", Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("음성 인식 서비스 사용 동의", modifier = Modifier.weight(1f)); Switch(m.consentSpeech, { m.consentSpeech = it; m.save() }, enabled = m.editingAllowed)
            }
            Text("음성 입력은 기기의 인식 서비스가 처리하며 제공자에 따라 네트워크로 전송될 수 있습니다. 앱은 음성 녹음과 상황 대화를 저장하지 않습니다. 위치는 요청 시에만 확인하며 저장하지 않습니다.", color = Muted)
            Text("암호화 저장: 가족·연락처·직접 등록 장소·설정\n백업 및 외부 분석 전송 없음", color = Muted)
            Action("이 기기의 모든 정보 삭제", Icons.Rounded.DeleteForever, m.editingAllowed) { reset = true }
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("저장한 정보를 모두 삭제할까요?") }, text = { Text("가족·의료 참고 정보·차량·보험·등록 장소·설정이 삭제됩니다.") },
        confirmButton = { TextButton(onClick = { if (m.editingAllowed) { m.deleteAll(); vehicle = ""; insurer = ""; phone = ""; mapToken = ""; mapServer = m.mapServer; checklist = m.checklist.joinToString(", ") }; reset = false }) { Text("모두 삭제") } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text("취소") } })
}
@Composable private fun Entry(value: String, changed: (String) -> Unit, label: String, enabled: Boolean = true) {
    OutlinedTextField(value, { changed(it.take(500)) }, label = { Text(label) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), maxLines = 3)
}
@Composable private fun FamilyEditor(person: FamilyMember?, dismiss: () -> Unit, save: (String, String, Set<Facility>, String, String, CompanionKind) -> Unit) {
    var name by remember { mutableStateOf(person?.name ?: "") }; var age by remember { mutableStateOf(person?.age ?: "") }
    var needs by remember { mutableStateOf(person?.needs ?: emptySet()) }; var medical by remember { mutableStateOf(person?.medical ?: "") }
    var phone by remember { mutableStateOf(person?.contact ?: "") }
    var kind by remember { mutableStateOf(person?.kind ?: CompanionKind.PERSON) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (person == null) "동승자 추가" else "동승자 수정") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompanionKind.entries.forEach { value -> FilterChip(kind == value, { kind = value }, label = { Text(value.label) }) }
            }
            Entry(name, { name = it }, "별칭 (필수)"); Entry(age, { age = it }, "연령대 / 나이")
            Text("필수 시설"); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Facility.entries.forEach { f -> FilterChip(f in needs, { needs = if (f in needs) needs - f else needs + f }, label = { Text(f.label) }) }
            }
            Entry(medical, { medical = it }, "알레르기·복용약 등 (선택)"); Entry(phone, { phone = it }, "보호자 전화번호 (선택)")
            if (phone.isNotBlank() && PhoneNumbers.clean(phone) == null) Text("전화번호 형식을 확인해 주세요", color = Red)
        }
    }, confirmButton = { TextButton(onClick = { save(name, age, needs, medical, phone, kind) }, enabled = name.isNotBlank() && (phone.isBlank() || PhoneNumbers.clean(phone) != null)) { Text("저장") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("취소") } })
}
@Composable private fun PlaceEditor(onDismiss: () -> Unit, onSave: (Place) -> Unit) {
    var name by remember { mutableStateOf("") }; var lat by remember { mutableStateOf("") }; var lon by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }; var facilities by remember { mutableStateOf(setOf<Facility>()) }
    val latitude = lat.toDoubleOrNull(); val longitude = lon.toDoubleOrNull()
    val dateOk = runCatching { !LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(false)
    val valid = name.isNotBlank() && latitude != null && latitude.isFinite() && latitude in -90.0..90.0 && longitude != null && longitude.isFinite() && longitude in -180.0..180.0 && source.isNotBlank() && dateOk
    AlertDialog(onDismissRequest = onDismiss, title = { Text("직접 확인한 장소 등록") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("확인한 시설만 선택하세요. 선택하지 않은 시설은 ‘미확인’으로 저장됩니다.")
            Entry(name, { name = it }, "장소명"); Entry(lat, { lat = it }, "위도 (-90~90)"); Entry(lon, { lon = it }, "경도 (-180~180)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Facility.entries.forEach { f -> FilterChip(f in facilities, { facilities = if (f in facilities) facilities - f else facilities + f }, label = { Text(f.label) }) } }
            Entry(source, { source = it }, "정보 출처 / 확인 방법 (필수)"); Entry(date, { date = it }, "확인일 YYYY-MM-DD")
            Entry(note, { note = it }, "시설 위치 등 참고사항")
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = {
        onSave(Place(UUID.randomUUID().toString(), name.trim(), latitude!!, longitude!!, Facility.entries.associateWith { if (it in facilities) Evidence.CONFIRMED else Evidence.UNKNOWN },
            "접근 방향 미확인", "사용자 등록: ${source.trim()}", date, "운영시간 미확인", note = note.trim()))
    }) { Text("등록") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
