package sg.act.domain.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiRedactorTest {

    @Test
    fun `redacts email addresses`() {
        val result = PiiRedactor.redact("contact me at jane.doe@example.com please")
        assertTrue(result.redactedText.contains("[EMAIL]"))
        assertFalse(result.redactedText.contains("jane.doe@example.com"))
        assertTrue(result.findings.any { it.kind == "email" })
    }

    @Test
    fun `redacts phone numbers`() {
        val result = PiiRedactor.redact("call +1 (415) 555-2671 tomorrow")
        assertTrue(result.redactedText.contains("[PHONE]"))
        assertTrue(result.hasFindings)
    }

    @Test
    fun `redacts ssn and credit card`() {
        val result = PiiRedactor.redact("ssn 123-45-6789 card 4111 1111 1111 1111")
        assertTrue(result.redactedText.contains("[SSN]"))
        assertTrue(result.redactedText.contains("[CARD]"))
    }

    @Test
    fun `leaves clean text untouched`() {
        val input = "What is the capital of France?"
        val result = PiiRedactor.redact(input)
        assertEquals(input, result.redactedText)
        assertFalse(result.hasFindings)
    }
}
