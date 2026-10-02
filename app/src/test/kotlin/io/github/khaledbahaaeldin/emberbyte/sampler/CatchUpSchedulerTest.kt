package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatchUpSchedulerTest {
    @Test fun schedule_enqueues_unique_periodic_catchup_work() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        CatchUpScheduler.schedule(app)
        val workInfos = WorkManager.getInstance(app).getWorkInfosForUniqueWork("catchup").get()
        assertNotNull(workInfos)
        assertEquals(1, workInfos.size)
        assertTrue(
            "Expected work to be ENQUEUED or RUNNING but was ${workInfos[0].state}",
            workInfos[0].state == WorkInfo.State.ENQUEUED || workInfos[0].state == WorkInfo.State.RUNNING,
        )
    }
}
