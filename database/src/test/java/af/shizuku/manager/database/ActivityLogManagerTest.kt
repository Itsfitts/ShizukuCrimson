package af.shizuku.manager.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay

class ActivityLogManagerTest : FunSpec({

    class FakeActivityLogDao : ActivityLogDao {
        var clearCalled = false

        override suspend fun insert(log: ActivityLogRoom): Long = 0L
        override suspend fun insertAll(logs: List<ActivityLogRoom>) {}
        override fun getAll(): Flow<List<ActivityLogRoom>> = kotlinx.coroutines.flow.emptyFlow()
        override fun getLimited(limit: Int): Flow<List<ActivityLogRoom>> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun clear() {
            clearCalled = true
        }
        override suspend fun deleteExcess(limit: Int): Int = 0
        override suspend fun deleteOlderThan(timestamp: Long): Int = 0
        override suspend fun getCount(): Int = 0
        override suspend fun delete(log: ActivityLogRoom) {}
        override suspend fun getOldest(): ActivityLogRoom? = null
    }

    test("clear resets state arrays and sends an empty request to the database") {
        // Setup fake DAO
        val fakeDao = FakeActivityLogDao()
        val daoField = ActivityLogManager::class.java.getDeclaredField("dao")
        daoField.isAccessible = true
        daoField.set(ActivityLogManager, fakeDao)

        // Inject records
        val recordsField = ActivityLogManager::class.java.getDeclaredField("records")
        recordsField.isAccessible = true
        val records = recordsField.get(ActivityLogManager) as MutableList<ActivityLogRecord>
        records.clear()
        records.add(ActivityLogRecord(timestamp = 1000L, appName = "App", packageName = "pkg", action = "START"))

        // Add dummy entry to logs state flow to verify it gets cleared
        val logsField = ActivityLogManager::class.java.getDeclaredField("_logs")
        logsField.isAccessible = true
        val logs = logsField.get(ActivityLogManager) as kotlinx.coroutines.flow.MutableStateFlow<List<ActivityLogRecord>>
        logs.value = listOf(ActivityLogRecord(timestamp = 1000L, appName = "App", packageName = "pkg", action = "START"))

        ActivityLogManager.getRecords().size shouldBe 1
        ActivityLogManager.logs.value.size shouldBe 1

        // Call clear
        ActivityLogManager.clear()

        delay(100)

        // Verify that the database gets an empty request and state arrays are reset
        ActivityLogManager.getRecords().size shouldBe 0
        ActivityLogManager.logs.value.size shouldBe 0
        fakeDao.clearCalled shouldBe true
    }
})
