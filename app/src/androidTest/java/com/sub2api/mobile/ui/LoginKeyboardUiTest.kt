package com.sub2api.mobile.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.ui.screens.LoginScreen
import com.sub2api.mobile.ui.theme.Sub2ApiTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class)
class LoginKeyboardUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun loginUsesRegularImeInputTypesWhileKeepingThePasswordMasked() {
        val editors = mutableListOf<EditorInfo>()
        compose.setContent {
            Sub2ApiTheme {
                InterceptPlatformTextInput(interceptor = { request, _ ->
                    val info = EditorInfo()
                    request.createInputConnection(info)
                    editors += info
                    awaitCancellation()
                }) {
                    LoginScreen(
                        state = AppUiState(serverInput = "https://api.example.com"),
                        onLogin = { _, _ -> }, onChangeServer = {}, onOpenUrl = {},
                    )
                }
            }
        }
        compose.onNodeWithText("邮箱").performClick()
        compose.waitUntil { editors.isNotEmpty() }
        compose.runOnIdle {
            val email = editors.last()
            assertEquals(InputType.TYPE_CLASS_TEXT, email.inputType and InputType.TYPE_MASK_CLASS)
            assertEquals(InputType.TYPE_TEXT_VARIATION_NORMAL, email.inputType and InputType.TYPE_MASK_VARIATION)
            assertEquals(EditorInfo.IME_FLAG_FORCE_ASCII, email.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII)
            assertEquals(EditorInfo.IME_ACTION_NEXT, email.imeOptions and EditorInfo.IME_MASK_ACTION)
        }

        compose.onNodeWithText("密码").performClick()
        compose.waitUntil { editors.size >= 2 }
        compose.onNodeWithText("密码").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.runOnIdle {
            val password = editors.last()
            assertEquals(InputType.TYPE_CLASS_TEXT, password.inputType and InputType.TYPE_MASK_CLASS)
            assertEquals(InputType.TYPE_TEXT_VARIATION_NORMAL, password.inputType and InputType.TYPE_MASK_VARIATION)
            assertEquals(0, password.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
            assertEquals(EditorInfo.IME_ACTION_DONE, password.imeOptions and EditorInfo.IME_MASK_ACTION)
        }
    }
}
