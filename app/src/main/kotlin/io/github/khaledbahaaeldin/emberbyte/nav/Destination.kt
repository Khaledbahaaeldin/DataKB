package io.github.khaledbahaaeldin.emberbyte.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.NavAccent
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.NavBarItem

enum class Destination(val id: String, val route: String, private val label: String, private val accent: NavAccent) {
    Home("home", "home", "Home", NavAccent.Primary),
    Apps("apps", "apps", "Apps", NavAccent.Tertiary),
    Plans("plans", "plans", "Plans", NavAccent.Secondary),
    Lens("lens", "lens", "Lens", NavAccent.Error),
    ;

    fun toNavBarItem(): NavBarItem = NavBarItem(
        id = id,
        label = label,
        accent = accent,
        icon = when (this) {
            Home -> Icons.Rounded.Home
            Apps -> Icons.Rounded.Apps
            Plans -> Icons.Rounded.AccountBalanceWallet
            Lens -> Icons.Rounded.Visibility
        },
    )

    companion object {
        fun fromRoute(route: String?): Destination =
            entries.firstOrNull { route == it.route || route?.startsWith(it.route + "/") == true } ?: Home
        fun fromId(id: String): Destination = entries.first { it.id == id }
    }
}
