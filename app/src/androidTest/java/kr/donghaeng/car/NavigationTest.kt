package kr.donghaeng.car

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun allFiveTabsRemainReachable() {
        rule.onNodeWithText("장소 찾기", useUnmergedTree = true).performClick()
        rule.onNodeWithText("우리 가족에게 맞는 다음 쉼").assertIsDisplayed()
        rule.onNodeWithText("SOS", useUnmergedTree = true).performClick()
        rule.onNodeWithText("지금, 도움이 필요한가요?").assertIsDisplayed()
        rule.onNodeWithText("가족", useUnmergedTree = true).performClick()
        rule.onNodeWithText("함께 타는 가족").assertIsDisplayed()
        rule.onNodeWithText("MY", useUnmergedTree = true).performClick()
        rule.onNodeWithText("MY · 이동 설정").assertIsDisplayed()
        rule.onNodeWithText("홈", useUnmergedTree = true).performClick()
        rule.onNodeWithText("함께 가는 길, 조금 더 편안하게").assertIsDisplayed()
    }
    @Test fun emergencyDialIsImmediatelyAvailable() {
        rule.onNodeWithText("SOS", useUnmergedTree = true).performClick()
        rule.onNodeWithText(" 119 구조·구급").assertIsDisplayed().performClick()
        rule.onNodeWithText("시연: 119 전화 화면 요청 · 실제 전화하지 않습니다").assertIsDisplayed()
    }
}
