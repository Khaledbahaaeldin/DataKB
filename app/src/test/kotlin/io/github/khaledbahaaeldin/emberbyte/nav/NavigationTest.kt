package io.github.khaledbahaaeldin.emberbyte.nav

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationTest {
    @Test fun tab_routes_map_to_their_destination() {
        assertEquals(Destination.Home, Destination.fromRoute("home"))
        assertEquals(Destination.Apps, Destination.fromRoute("apps"))
        assertEquals(Destination.Plans, Destination.fromRoute("plans"))
        assertEquals(Destination.Lens, Destination.fromRoute("lens"))
    }

    @Test fun the_app_detail_route_keeps_the_apps_tab_selected() {
        assertEquals(Destination.Apps, Destination.fromRoute(Routes.APP_DETAIL))
        assertEquals(Destination.Apps, Destination.fromRoute(Routes.appDetail("com.example.app")))
    }

    @Test fun other_routes_and_null_fall_back_to_home() {
        assertEquals(Destination.Home, Destination.fromRoute(Routes.HISTORY))
        assertEquals(Destination.Home, Destination.fromRoute(Routes.SETTINGS))
        assertEquals(Destination.Home, Destination.fromRoute(Routes.ONBOARDING))
        assertEquals(Destination.Home, Destination.fromRoute(null))
    }

    @Test fun a_tab_name_inside_another_word_does_not_match() =
        assertEquals(Destination.Home, Destination.fromRoute("appsettings"))

    @Test fun package_names_are_encoded_into_the_route() {
        assertEquals("apps/com.example.app", Routes.appDetail("com.example.app"))
        assertEquals("apps/uid%3A10123", Routes.appDetail("uid:10123"))
    }
}
