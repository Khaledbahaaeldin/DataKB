package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    @Test fun catchup_worker_does_not_start_service_when_onboarding_incomplete() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<EmberbyteApplication>()
        val worker = CatchUpWorker(app, createWorkerParameters())
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun catchup_worker_starts_service_when_onboarding_completed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<EmberbyteApplication>()
        app.graph.onboarding.complete()
        val worker = CatchUpWorker(app, createWorkerParameters())
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(SamplerService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
    }

    private fun createWorkerParameters(): WorkerParameters {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(unsafe, WorkerParameters::class.java) as WorkerParameters
    }
}
