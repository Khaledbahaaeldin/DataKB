package io.github.khaledbahaaeldin.emberbyte.nav

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeepLinksTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test fun links_are_built_from_ids_and_package_names() {
        assertEquals("emberbyte://plans/7", DeepLinks.plan(7))
        assertEquals("emberbyte://apps/com.example.app", DeepLinks.app("com.example.app"))
        assertEquals("plans/0", Routes.planEditor(0))
    }

    @Test fun the_explicit_intent_targets_the_main_activity_and_carries_the_uri() {
        val intent = DeepLinks.intent(context, DeepLinks.plan(3))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("emberbyte://plans/3", intent.data.toString())
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun every_documented_link_resolves_to_the_main_activity_through_the_manifest() {
        val uris = listOf(DeepLinks.HOME, DeepLinks.APPS, DeepLinks.app("com.example"), DeepLinks.PLANS, DeepLinks.plan(3), DeepLinks.LENS, DeepLinks.SETTINGS)
        for (uri in uris) {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(context.packageName)
            val resolved = context.packageManager.queryIntentActivities(probe, 0)
            assertTrue("$uri did not resolve", resolved.any { it.activityInfo.name == MainActivity::class.java.name })
        }
    }

    @Test fun other_schemes_do_not_resolve() {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/plans/1")).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(probe, 0).none { it.activityInfo.name == MainActivity::class.java.name })
    }
}
