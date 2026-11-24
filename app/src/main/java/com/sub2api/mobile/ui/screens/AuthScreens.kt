package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.ServerUrl
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons
import com.sub2api.mobile.ui.theme.Ink
import com.sub2api.mobile.ui.theme.Mint

@Composable
private fun BrandHeader(siteName: String = "Sub2API", subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(50.dp).clip(AppCardShape).background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            Text("[ ]", color = Mint, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Column {
            Text(siteName.ifBlank { "Sub2API" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AuthPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardShape,
        color = MaterialTheme.colorScheme.surface,
        border = appCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun ServerSetupScreen(state: AppUiState, onConnect: (String) -> Unit, onManageServers: () -> Unit = {}) {
    var server by rememberSaveable(state.serverInput) { mutableStateOf(state.serverInput) }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 44.dp),
        ) {
            BrandHeader(subtitle = "连接服务器")
            Spacer(Modifier.height(32.dp))
            AuthPanel {
                state.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("服务器地址", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Sub2API 部署地址", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("https://api.example.com") },
                    leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                    singleLine = true,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConnect(server) }),
                    isError = state.serverError != null,
                    supportingText = state.serverError?.let { error -> ({ Text(error) }) },
                )
                Button(onClick = { onConnect(server) }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !state.loading && server.isNotBlank()) {
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("连接")
                }
                TextButton(onClick = onManageServers, modifier = Modifier.fillMaxWidth()) { Text("服务管理 / 添加服务") }
            }
        }
    }
}

@Composable
fun LoginScreen(
    state: AppUiState,
    onLogin: (String, String) -> Unit,
    onChangeServer: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var email by rememberSaveable(state.activeServerId) { mutableStateOf("") }
    var password by remember(state.activeServerId) { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    val webRoot = remember(state.serverInput) { runCatching { ServerUrl.webRoot(ServerUrl.normalize(state.serverInput)) }.getOrDefault(state.serverInput) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 38.dp),
        ) {
            BrandHeader(siteName = settings?.siteName ?: "Sub2API", subtitle = settings?.siteSubtitle?.ifBlank { "账户登录" } ?: "账户登录")
            Spacer(Modifier.height(28.dp))
            AuthPanel {
                Text("欢迎回来", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("邮箱") },
                    leadingIcon = { Icon(Icons.Outlined.Mail, null) },
                    singleLine = true,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (passwordVisible) "隐藏密码" else "显示密码")
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.loading,
                    // Keep the visual mask independent of the IME type so OEM password keyboards are not requested.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onLogin(email, password) }),
                )
                Button(onClick = { onLogin(email, password) }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("登录")
                }
                if (settings?.registrationEnabled == true || settings?.passwordResetEnabled == true) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (settings.registrationEnabled) TextButton(onClick = { onOpenUrl("$webRoot/register") }) { Text("注册账户") }
                        if (settings.passwordResetEnabled) TextButton(onClick = { onOpenUrl("$webRoot/forgot-password") }) { Text("找回密码") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Dns, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("切换服务 · ${state.activeServerName.ifBlank { state.serverInput }}", maxLines = 1)
            }
            TextButton(onClick = { onOpenUrl(webRoot) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("打开网页版")
            }
        }
    }
}

@Composable
fun TwoFactorScreen(state: AppUiState, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            }
            Spacer(Modifier.height(32.dp))
            AuthPanel {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(54.dp).clip(AppCardShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("两步验证", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (state.twoFactorHint.isNotBlank()) {
                        Text(state.twoFactorHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("6 位验证码") },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit(code) }),
                    )
                    Button(onClick = { onSubmit(code) }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !state.loading && code.length == 6) {
                        if (state.loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("验证")
                    }
                }
            }
        }
    }
}
