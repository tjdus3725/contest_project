# 동행 · 가족 이동 케어

**가족에게 필요한 다음 쉼을 찾고, 위기 상황에서 다음 행동과 도움 연결을 지원하는 차량용 Android 시제품.**

현대자동차그룹 차량용 앱 개발 공모전의 [개발환경 가이드](https://appdevchallenge.auto-hmg.io/guide/development)를 기준으로 작성했습니다. 웹사이트나 Android Auto 템플릿 앱이 아니라 **Pleos Connect / Android Automotive OS용 네이티브 Android 앱**입니다.

[검증 결과와 실제 화면](docs/VALIDATION.md) · [5분 시연 절차](docs/DEMO.md) · [기능·데이터 명세](docs/FEATURES.md)

**실제 지도 기능 추가:** [카카오 키 발급·서버 실행·지도 설정 안내](docs/KAKAO_SETUP.md)를 따라 연결하면 실제 장소 검색, 자동차 경로, 경로 주변 마커, 경유 추가 시간 비교를 사용할 수 있습니다. 가족 프로필은 반려견·반려묘도 지원합니다. 실제 서비스 호출에는 본인 카카오 키와 실행 중인 서버가 필요합니다.

## 구현 기능

| 탭 | 동작 |
|---|---|
| 홈 | 동승자 요약, 이동 시작/종료, 휴식 타이머, 빠른 시설 검색, 준비물 확인 |
| 장소 찾기 | 실제 카카오 지도·장소 검색, 출발·목적지와 최단 거리/시간 경로, 주변 마커·경유 추가 시간, 직접 확인한 시설 목록, Pleos 목적지·경유지 요청 |
| SOS | 즉시 119/112/보험사 전화 화면 요청, 문장 분류, 위험 신호 우선 처리, 상황별 고정 안내, 한 단계씩 보기·읽기, 위치 요청, 동승자 참고 정보 |
| 가족 | 사람·반려견·반려묘·기타 반려동물 추가·수정·삭제, 이번 이동 선택, 필수 시설, 선택적 응급 참고 정보와 보호자 전화 |
| MY | 차량·보험, 휴식 간격, 준비물 편집, 음성 처리 동의, 연결 상태, 데이터 삭제 |

## 실행 환경

- Java 17, Gradle **8.11**, Android Gradle Plugin 8.7.3
- Kotlin 2.0.21, Jetpack Compose, minSdk **28**, compileSdk 35, targetSdk 34
- Pleos Connect Emulator **2.1.0 / API 34**
- NaviHelper **3.0.5** (`ai.pleos.playground:NaviHelper:3.0.5`)
- Android SDK Platform 35 / Build Tools 34.0.0

Android Studio에서 저장소를 열고 Gradle Sync를 실행하세요. SDK 경로는 IDE가 생성하는 `local.properties`에만 기록하며 저장소에 올리지 않습니다. Windows에서 경로 문제가 발생하면 영문 경로에 체크아웃해 주세요.

```sh
# macOS/Linux (Windows는 gradlew.bat)
./gradlew :core:test :app:assembleDemoDebug :app:assemblePleosDebug
./gradlew :app:lintDemoDebug :app:lintPleosDebug
./gradlew :app:connectedDemoDebugAndroidTest
```

## 두 가지 빌드

### demoDebug — 일반 Android / 태블릿 시연

- 패키지 `kr.donghaeng.car.demo`
- 실제 지도·장소 검색은 카카오 키를 설정한 로컬 서버에 연결합니다. 가상 쉼터는 화면에서 제거했습니다.
- 실제 내비게이션을 변경하지 않습니다.
- 119·112·보험사 버튼은 **모의 실행**하며 전화 화면을 열지 않습니다.
- 마이크/한국어 음성 엔진/위치 제공자가 없으면 화면에 대체 안내를 표시합니다.

```sh
adb install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
adb shell am start -n kr.donghaeng.car.demo/kr.donghaeng.car.MainActivity
```

### pleosDebug — Pleos Connect 차량 환경

- 패키지 `kr.donghaeng.car`
- 공식 NaviHelper 저장소와 타입을 직접 사용합니다. 초기화·이벤트 등록·오류·시간 초과·정리를 구현했습니다.
- `차량에 목적지 요청`으로 목적지를 설정하거나 차량 내비게이션에 **목적지를 먼저 설정**한 뒤 실제 검색 장소를 경유지로 요청합니다.
- `WaypointIndex.FIRST`를 사용합니다. 기존 경유지 변경 가능성을 화면에서 고지하고 확인 후 요청합니다.
- 가상 장소는 실제 SDK에 전송하지 않습니다. 수신 이벤트를 임의로 성공 판정하지 않고 반영 여부를 차량 내비게이션에서 확인하도록 표시합니다.
- 전화는 `ACTION_DIAL`로 전화 화면만 요청합니다. 자동 신고·자동 통화·출동 완료 표시는 없습니다. 차량에 전화 앱이 없으면 연결된 휴대전화 사용을 안내합니다.

공식 [에뮬레이터 설치 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 SDK Update Sites에 다음 Windows/Linux 주소를 추가하세요.

```text
https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
```

Automotive → Pleos Connect v2.1.0 → API 34, Cold boot, RAM 4GB 설정 후 실행합니다.

```sh
adb install -r app/build/outputs/apk/pleos/debug/app-pleos-debug.apk
adb shell am start -n kr.donghaeng.car/kr.donghaeng.car.MainActivity
```

## AI와 긴급 안내의 실제 범위

`core/SituationAssistant.kt`는 한국어 문자 bigram의 nearest-example 분류 모델입니다. 앱 안에서 예시 말뭉치를 벡터로 만들고 가까운 예시 문장과의 유사도로 상황을 분류합니다. 인터넷이나 API 키 없이 실행됩니다. **LLM이나 의학적 진단 모델은 아닙니다.**

위험 신호 규칙이 분류 모델보다 먼저 적용되며, 분류가 애매하면 판단을 보류합니다. 의료·화재·침수·사고 표현은 휴식 장소 요청보다 우선합니다. 위험 신호 부정문도 보수적으로 처리하므로 과잉 경고가 가능합니다. 출력은 사전에 작성한 안내이며 자유 생성 처치·약물 용량·안전 확정은 없습니다.

**현재 말뭉치는 소규모 개발용입니다. 안내문과 모델은 의료·소방 전문가 검수 및 실제 안전성 평가를 통과한 제품이 아닙니다.** 배포 전 전문가 검토, 방언·오인식·부정문·다국어·아동별 시나리오 평가가 필요합니다. 급박한 상황의 판단을 앱에 맡기지 않도록 긴급 전화는 항상 먼저 표시합니다.

## 개인정보와 차량 제한

- 가족·연락처·설정은 Android Keystore AES-GCM으로 암호화해 앱 내부 저장소에 보관합니다.
- 백업 비활성화, 대화·녹음·위치 영구 저장 없음, 외부 AI 호출 및 분석 SDK 없음. 지도 사용 동의 시 지도 SDK가 로드되고 검색어·출발/도착/경유 좌표가 설정 서버 및 카카오로 전송됩니다. 가족 의료 정보는 지도 서버에 보내지 않습니다.
- Android 음성 인식 제공자가 오디오를 네트워크 처리할 수 있으므로 사전 동의 후 사용합니다.
- 위치는 버튼 요청 시 최대 30초만 수신합니다. 좌표·정확도·수신시각을 표시합니다. 최신 위치임을 보장하지 않습니다.
- Automotive에서는 AAOS UX restrictions를 읽어 상세 입력을 제한합니다. 연결 실패/알 수 없음도 편집 잠금입니다.
- `distractionOptimized` 승인을 주장하지 않습니다. **현재 빌드는 정차 중심 시제품**이며, 차량 시스템이 주행 중 앱 전체를 제한할 수 있습니다.
- 출시용 서명 키, 권한 승인, 앱 마켓 등록은 포함하지 않습니다.

## 데이터와 미구현 범위

시설 데이터는 Pleos SDK가 제공한다고 가정하지 않습니다. 카카오에서 실제 장소명·주소·좌표를 조회하지만 기저귀 교환대·수유실·반려동물 출입·운영시간은 미확인입니다. 사용자가 직접 확인한 시설은 출처·확인일과 함께 별도로 등록합니다. 사용자 확인은 운영기관 검증과 다릅니다. 기존 가상 데이터는 단위 테스트 fixture에만 남아 있습니다.

경유 경로를 다시 조회해 예상 추가 시간·거리를 비교합니다. 경로 주변 검색은 최대 8개 지점의 부분 검색이며 전체 시설을 보장하지 않습니다. 현재 제공하지 않는 기능: 실시간 턴바이턴 길 안내 엔진, 반대편 도로 자동 제외, 운영시간·반려동물 출입 실시간 검증, 자동 수면/멀미 감지, 공조 제어, 구조기관 데이터 전송, 보호자 자동 공유, 클라우드 동기화, 백그라운드 휴식 푸시. 휴식 안내는 앱 화면 사용 중 작동하며 이동 세션은 프로세스 종료 시 초기화됩니다.

## 구성

```text
app/src/main/       Compose UI, 플랫폼 음성·위치·차량 제한, 암호화 저장
app/src/demo/       내비게이션 모의 구현
app/src/pleos/      NaviHelper 실제 구현 및 권한
core/              시설 필터, 동승자 조건, 상황 모델, 위험 규칙, 단위 테스트
server/            카카오 REST 프록시·웹 지도·경로 주변 장소 필터·서버 테스트
docs/              기능 명세, 검증 및 시연 안내
.github/workflows/ 두 APK 빌드·단위 테스트·Lint·에뮬레이터 UI 테스트
```

APK는 GitHub Actions의 **Android build and tests → Artifacts → donghaeng-apks-and-reports**에서 받을 수 있습니다. 실제 결과는 실행 상태를 확인해 주세요. Pleos 실차/전용 에뮬레이터 검증은 일반 Android UI 테스트와 별도입니다.

## 근거 문서

- [공모전 개발환경](https://appdevchallenge.auto-hmg.io/guide/development)
- [SDK 제공·신청 상태](https://document.pleos.ai/docs/pleos-only/create-my-project)
- [NaviHelper 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [경유지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)
- [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [소방청 119 구급신고 요령](https://www.nfa.go.kr/nfa/safetyinfo/emergencyservice/119emergencydeclaration)

문서 확인 기준: 2026-10-24. FusedLocation/STT/TTS의 제공 상태는 프로젝트 포털에서 재확인해야 합니다. 이 앱의 음성·위치 구현은 Android 대체 서비스입니다.
