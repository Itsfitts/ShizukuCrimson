package af.shizuku.manager.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Field

class AppContextManagerTest {

    private val validJson = """
        {
            "apps": {
                "com.test.app": {
                    "description": "A test app",
                    "enhancements": ["shell_interceptor", "invalid_enhancement"],
                    "verified": true,
                    "root_support": "partial",
                    "shizuku_aware": true
                }
            }
        }
    """.trimIndent()

    private val validJson2 = """
        {
            "apps": {
                "com.test.app": {
                    "description": "A test app 2",
                    "enhancements": ["storage_proxy"],
                    "verified": false,
                    "root_support": "full",
                    "shizuku_aware": false
                }
            }
        }
    """.trimIndent()

    private val invalidJson = """
        {
            "apps": {
                "com.test.app": {
                    "description":
    """.trimIndent()

    private class MockAppContextSettings : AppContextSettings {
        var remoteDbJsonValue: String? = null
        var lastDbUpdateValue: Long = 0

        override fun getRemoteDbJson(): String? = remoteDbJsonValue

        override fun setRemoteDbJson(json: String) {
            remoteDbJsonValue = json
        }

        override fun setLastDbUpdate(time: Long) {
            lastDbUpdateValue = time
        }
    }

    private lateinit var settings: MockAppContextSettings

    @BeforeEach
    fun setup() {
        settings = MockAppContextSettings()
        // Reset the dynamic database to empty
        val dbField: Field = AppContextManager::class.java.getDeclaredField("dynamicDatabase")
        dbField.isAccessible = true
        val dynamicDatabase = dbField.get(AppContextManager) as MutableMap<String, AppContextManager.AppMetadata>
        dynamicDatabase.clear()

        AppContextManager.initialize(settings)
    }

    @Test
    fun `test valid json updates metadata and settings`() {
        // Pre-condition: empty metadata for test package
        AppContextManager.getMetadata("com.test.app").shouldBeNull()

        AppContextManager.updateDatabase(validJson)

        val metadata = AppContextManager.getMetadata("com.test.app")
        metadata.shouldNotBeNull()
        metadata.description shouldBe "A test app"
        metadata.isVerified shouldBe true
        metadata.rootSupportLevel shouldBe RootSupportLevel.PARTIAL
        metadata.supportsShizukuNatively shouldBe true

        val enhancements = metadata.potentialEnhancements
        enhancements.size shouldBe 1
        enhancements.first().key shouldBe "shell_interceptor"

        settings.remoteDbJsonValue shouldBe validJson
        (settings.lastDbUpdateValue > 0) shouldBe true
    }

    @Test
    fun `test subsequent updates replace existing metadata`() {
        AppContextManager.updateDatabase(validJson)
        var metadata = AppContextManager.getMetadata("com.test.app")
        metadata.shouldNotBeNull()
        metadata.description shouldBe "A test app"

        AppContextManager.updateDatabase(validJson2)
        metadata = AppContextManager.getMetadata("com.test.app")
        metadata.shouldNotBeNull()
        metadata.description shouldBe "A test app 2"
        metadata.isVerified shouldBe false
        metadata.rootSupportLevel shouldBe RootSupportLevel.FULL
        metadata.supportsShizukuNatively shouldBe false

        val enhancements = metadata.potentialEnhancements
        enhancements.size shouldBe 1
        enhancements.first().key shouldBe "storage_proxy"
    }

    @Test
    fun `test invalid json does not crash but clears data`() {
        // Setup initial data
        AppContextManager.updateDatabase(validJson)
        AppContextManager.getMetadata("com.test.app").shouldNotBeNull()

        // Apply invalid JSON
        AppContextManager.updateDatabase(invalidJson)

        // Database should be cleared, leaving no data for "com.test.app"
        AppContextManager.getMetadata("com.test.app").shouldBeNull()

        // Setting should still reflect the updated invalid JSON
        settings.remoteDbJsonValue shouldBe invalidJson
    }
}
