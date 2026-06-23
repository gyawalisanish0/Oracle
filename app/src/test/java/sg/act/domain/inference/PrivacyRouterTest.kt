package sg.act.domain.inference

import sg.act.domain.data.model.Message
import sg.act.domain.data.model.Route
import sg.act.domain.privacy.PrivacyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyRouterTest {

    private class FakeEngine(
        override val displayName: String,
        private val reply: (String) -> String,
    ) : InferenceEngine {
        var lastPrompt: String? = null
        override fun generate(prompt: String, history: List<Message>): Flow<String> = flow {
            lastPrompt = prompt
            emit(reply(prompt))
        }
    }

    private val local = FakeEngine("local") { "local:$it" }

    private suspend fun PrivacyRouter.StreamingOutcome.text(): String =
        tokens.toList().joinToString("")

    @Test
    fun `local by default when cloud not requested`() = runTest {
        val router = PrivacyRouter(local, remoteProvider = { error("must not be called") })
        val outcome = router.answer(
            prompt = "hi",
            history = emptyList(),
            privacy = PrivacyState(),
            useCloudForThisTurn = false,
        )
        assertEquals(Route.LOCAL, outcome.route)
        assertEquals("local:hi", outcome.text())
    }

    @Test
    fun `kill switch forces local fallback even when cloud requested`() = runTest {
        val cloud = FakeEngine("cloud") { "cloud:$it" }
        val router = PrivacyRouter(local, remoteProvider = { PrivacyRouter.Remote(cloud) })
        val outcome = router.answer(
            prompt = "hi",
            history = emptyList(),
            privacy = PrivacyState(networkKillSwitch = true, cloudConsentGiven = true),
            useCloudForThisTurn = true,
        )
        assertEquals(Route.LOCAL, outcome.route)
        assertEquals("local:hi", outcome.text())
        assertNull(cloud.lastPrompt) // cloud engine was never touched
        assertNotNull(outcome.note)
    }

    @Test
    fun `routes to cloud with redacted payload when allowed`() = runTest {
        val cloud = FakeEngine("cloud") { "cloud:$it" }
        val router = PrivacyRouter(local, remoteProvider = { PrivacyRouter.Remote(cloud) })
        val outcome = router.answer(
            prompt = "email me at a@b.com",
            history = emptyList(),
            privacy = PrivacyState(
                networkKillSwitch = false,
                cloudConsentGiven = true,
                redactBeforeCloud = true,
            ),
            useCloudForThisTurn = true,
        )
        assertEquals(Route.CLOUD, outcome.route)
        assertTrue(outcome.sentPayloadPreview!!.contains("[EMAIL]"))
        assertTrue(outcome.text().contains("cloud:"))
        assertTrue(cloud.lastPrompt!!.contains("[EMAIL]")) // raw PII never reached cloud
    }

    @Test
    fun `data-logging model blocked without data-logging consent`() = runTest {
        val cloud = FakeEngine("cloud") { "cloud:$it" }
        val router = PrivacyRouter(local, remoteProvider = { PrivacyRouter.Remote(cloud, logsData = true) })
        val outcome = router.answer(
            prompt = "hi",
            history = emptyList(),
            privacy = PrivacyState(
                networkKillSwitch = false,
                cloudConsentGiven = true,
                allowDataLoggingModels = false,
            ),
            useCloudForThisTurn = true,
        )
        assertEquals(Route.LOCAL, outcome.route)
        assertNull(cloud.lastPrompt) // data-logging model never touched
        assertNotNull(outcome.note)
    }

    @Test
    fun `data-logging model allowed with explicit consent`() = runTest {
        val cloud = FakeEngine("cloud") { "cloud:$it" }
        val router = PrivacyRouter(local, remoteProvider = { PrivacyRouter.Remote(cloud, logsData = true) })
        val outcome = router.answer(
            prompt = "hi",
            history = emptyList(),
            privacy = PrivacyState(
                networkKillSwitch = false,
                cloudConsentGiven = true,
                allowDataLoggingModels = true,
            ),
            useCloudForThisTurn = true,
        )
        assertEquals(Route.CLOUD, outcome.route)
        assertEquals("cloud:hi", outcome.text())
    }

    @Test
    fun `falls back to local when no provider configured`() = runTest {
        val router = PrivacyRouter(local, remoteProvider = { null })
        val outcome = router.answer(
            prompt = "hi",
            history = emptyList(),
            privacy = PrivacyState(networkKillSwitch = false, cloudConsentGiven = true),
            useCloudForThisTurn = true,
        )
        assertEquals(Route.LOCAL, outcome.route)
        assertEquals("local:hi", outcome.text())
        assertNotNull(outcome.note)
    }
}
