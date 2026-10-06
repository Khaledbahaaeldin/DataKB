package io.github.khaledbahaaeldin.emberbyte.nav

import androidx.core.net.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeepLinkTargetTest {

    @Test
    fun parse_tabs() {
        assertEquals(DeepLinkTarget.Tab(Destination.Home), DeepLinkTarget.parse("emberbyte://home".toUri()))
        assertEquals(DeepLinkTarget.Tab(Destination.Apps), DeepLinkTarget.parse("emberbyte://apps".toUri()))
        assertEquals(DeepLinkTarget.Tab(Destination.Plans), DeepLinkTarget.parse("emberbyte://plans".toUri()))
        assertEquals(DeepLinkTarget.Tab(Destination.Lens), DeepLinkTarget.parse("emberbyte://lens".toUri()))
    }

    @Test
    fun parse_settings() {
        assertEquals(DeepLinkTarget.Settings, DeepLinkTarget.parse("emberbyte://settings".toUri()))
    }

    @Test
    fun parse_app_detail() {
        assertEquals(
            DeepLinkTarget.AppDetail("com.example.app"),
            DeepLinkTarget.parse("emberbyte://apps/com.example.app".toUri()),
        )
    }

    @Test
    fun parse_app_detail_percent_encoded() {
        assertEquals(
            DeepLinkTarget.AppDetail("com.example.special app"),
            DeepLinkTarget.parse("emberbyte://apps/com.example.special%20app".toUri()),
        )
    }

    @Test
    fun parse_plan_editor() {
        assertEquals(DeepLinkTarget.PlanEditor(7L), DeepLinkTarget.parse("emberbyte://plans/7".toUri()))
        assertEquals(DeepLinkTarget.PlanEditor(0L), DeepLinkTarget.parse("emberbyte://plans/0".toUri()))
    }

    @Test
    fun parse_invalid_and_external_links_return_null() {
        assertNull(DeepLinkTarget.parse("emberbyte://plans/abc".toUri()))
        assertNull(DeepLinkTarget.parse("emberbyte://plans/-1".toUri()))
        assertNull(DeepLinkTarget.parse("emberbyte://nowhere".toUri()))
        assertNull(DeepLinkTarget.parse("https://example.com/plans/1".toUri()))
        assertNull(DeepLinkTarget.parse("emberbyte://".toUri()))
        assertNull(DeepLinkTarget.parse("emberbyte://plans/1/2".toUri()))
        assertNull(DeepLinkTarget.parse("emberbyte://home/extra".toUri()))
        assertNull(DeepLinkTarget.parse("emberbyte://settings/extra".toUri()))
        assertNull(DeepLinkTarget.parse("emberbyte://lens/extra".toUri()))
        assertNull(DeepLinkTarget.parse("emberbyte://apps/com.a/extra".toUri()))
    }
}
