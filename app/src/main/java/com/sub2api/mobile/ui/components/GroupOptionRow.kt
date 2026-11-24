package com.sub2api.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon as MaterialIcon
import com.sub2api.mobile.R
import com.sub2api.mobile.data.Group
import java.math.BigDecimal
import java.util.Locale

internal enum class GroupBrand(val label: String) {
    OPENAI("OpenAI"), CLAUDE("Claude"), DEEPSEEK("DeepSeek"), GROK("Grok"), GEMINI("Gemini"), OTHER("其他平台")
}

internal fun groupBrand(group: Group): GroupBrand {
    val platform = group.platform.trim().lowercase(Locale.ROOT)
    return when (platform) {
        "anthropic", "claude" -> GroupBrand.CLAUDE
        "deepseek" -> GroupBrand.DEEPSEEK
        "grok", "xai" -> GroupBrand.GROK
        "gemini", "google", "antigravity" -> GroupBrand.GEMINI
        "openai", "" -> {
            // OpenAI-compatible groups can serve other providers; prefer an explicit brand in the name.
            val name = group.name.lowercase(Locale.ROOT)
            when {
                "deepseek" in name || "深度求索" in name -> GroupBrand.DEEPSEEK
                "grok" in name -> GroupBrand.GROK
                "claude" in name -> GroupBrand.CLAUDE
                "gemini" in name -> GroupBrand.GEMINI
                platform == "openai" || listOf("gpt", "openai", "codex").any { it in name } -> GroupBrand.OPENAI
                else -> GroupBrand.OTHER
            }
        }
        else -> GroupBrand.OTHER
    }
}

internal fun groupRateLabel(rate: Double): String =
    if (rate.isFinite() && rate >= 0) "${BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString()}x 倍率"
    else "倍率未知"

@Composable
internal fun GroupOptionRow(group: Group, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val brand = groupBrand(group)
    val (icon, accent) = when (brand) {
        GroupBrand.OPENAI -> R.drawable.ic_brand_openai to colors.primary
        GroupBrand.CLAUDE -> R.drawable.ic_brand_claude to colors.tertiary
        GroupBrand.DEEPSEEK -> R.drawable.ic_brand_deepseek to if (dark) Color(0xFF91B7FF) else Color(0xFF2456B5)
        GroupBrand.GROK -> R.drawable.ic_brand_grok to colors.onSurface
        GroupBrand.GEMINI -> R.drawable.ic_brand_gemini to if (dark) Color(0xFFC7ACFF) else Color(0xFF7040AD)
        GroupBrand.OTHER -> R.drawable.ic_app_dns to colors.onSurfaceVariant
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.1f) else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.background(accent.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MaterialIcon(painterResource(icon), brand.label.takeUnless { brand == GroupBrand.OTHER }, Modifier.size(18.dp), tint = accent)
                    Text(group.name, color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                }
            }
            Text(
                groupRateLabel(group.rateMultiplier),
                modifier = Modifier.background(accent.copy(alpha = 0.1f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Box(Modifier.padding(top = 4.dp).size(20.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    MaterialIcon(painterResource(R.drawable.ic_group_selected), null, Modifier.size(20.dp), tint = colors.primary)
                }
            }
        }
        group.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
