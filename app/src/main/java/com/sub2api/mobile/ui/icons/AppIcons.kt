package com.sub2api.mobile.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import com.sub2api.mobile.R

@Immutable
internal data class AppIcon(
    @DrawableRes val resourceId: Int,
    val autoMirror: Boolean = false,
)

internal object Icons {
    object Outlined {
        val AccountBalanceWallet = AppIcon(R.drawable.ic_app_account_balance_wallet)
        val AccountCircle = AppIcon(R.drawable.ic_app_account_circle)
        val Add = AppIcon(R.drawable.ic_app_add)
        val BarChart = AppIcon(R.drawable.ic_app_bar_chart)
        val Bolt = AppIcon(R.drawable.ic_app_bolt)
        val CheckCircle = AppIcon(R.drawable.ic_app_check_circle)
        val ChevronRight = AppIcon(R.drawable.ic_app_chevron_right)
        val Close = AppIcon(R.drawable.ic_app_close)
        val ContentCopy = AppIcon(R.drawable.ic_app_content_copy)
        val Cookie = AppIcon(R.drawable.ic_app_cookie)
        val CreditCard = AppIcon(R.drawable.ic_app_credit_card)
        val Dashboard = AppIcon(R.drawable.ic_app_dashboard)
        val DataUsage = AppIcon(R.drawable.ic_app_data_usage)
        val Delete = AppIcon(R.drawable.ic_app_delete)
        val Description = AppIcon(R.drawable.ic_app_description)
        val Dns = AppIcon(R.drawable.ic_app_dns)
        val Edit = AppIcon(R.drawable.ic_app_edit)
        val ErrorOutline = AppIcon(R.drawable.ic_app_error_outline)
        val ExpandMore = AppIcon(R.drawable.ic_app_expand_more)
        val Inbox = AppIcon(R.drawable.ic_app_inbox)
        val Key = AppIcon(R.drawable.ic_app_key)
        val Language = AppIcon(R.drawable.ic_app_language)
        val Lock = AppIcon(R.drawable.ic_app_lock)
        val Mail = AppIcon(R.drawable.ic_app_mail)
        val Memory = AppIcon(R.drawable.ic_app_memory)
        val MoreVert = AppIcon(R.drawable.ic_app_more_vert)
        val NetworkCheck = AppIcon(R.drawable.ic_app_network_check)
        val OpenInBrowser = AppIcon(R.drawable.ic_app_open_in_browser)
        val Payments = AppIcon(R.drawable.ic_app_payments)
        val Redeem = AppIcon(R.drawable.ic_app_redeem)
        val Refresh = AppIcon(R.drawable.ic_app_refresh)
        val Schedule = AppIcon(R.drawable.ic_app_schedule)
        val Search = AppIcon(R.drawable.ic_app_search)
        val SystemUpdate = AppIcon(R.drawable.ic_app_system_update)
        val Token = AppIcon(R.drawable.ic_app_token)
        val Visibility = AppIcon(R.drawable.ic_app_visibility)
        val VisibilityOff = AppIcon(R.drawable.ic_app_visibility_off)
    }

    object AutoMirrored {
        object Outlined {
            val ArrowBack = AppIcon(R.drawable.ic_app_arrow_back, autoMirror = true)
            val Logout = AppIcon(R.drawable.ic_app_logout, autoMirror = true)
        }
    }
}

@Composable
internal fun Icon(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val mirror = icon.autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    MaterialIcon(
        painter = painterResource(icon.resourceId),
        contentDescription = contentDescription,
        modifier = modifier
            .then(Modifier.scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f))
            .then(Modifier.size(24.dp)),
        tint = tint,
    )
}
