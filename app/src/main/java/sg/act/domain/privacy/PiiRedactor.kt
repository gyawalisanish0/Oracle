package sg.act.domain.privacy

/**
 * Redacts common personally-identifiable information before any text is allowed
 * to leave the device for the cloud provider. The redactor is deliberately
 * conservative: it favours over-redaction. The redacted preview is shown to the
 * user for confirmation, so they always see exactly what would be transmitted.
 *
 * This is pure, dependency-free logic so it can be unit-tested in isolation.
 */
object PiiRedactor {

    data class Result(
        val redactedText: String,
        val findings: List<Finding>,
    ) {
        val hasFindings: Boolean get() = findings.isNotEmpty()
    }

    data class Finding(val kind: String, val original: String)

    private data class Rule(val kind: String, val regex: Regex, val placeholder: String)

    // Order matters: tightly-structured patterns (SSN, card, IP) must run before
    // the deliberately greedy phone matcher, or the phone rule would swallow them
    // and the more specific label would never be applied.
    private val rules = listOf(
        Rule(
            kind = "email",
            regex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
            placeholder = "[EMAIL]",
        ),
        Rule(
            kind = "ssn",
            regex = Regex("""\b\d{3}-\d{2}-\d{4}\b"""),
            placeholder = "[SSN]",
        ),
        Rule(
            kind = "credit-card",
            regex = Regex("""\b(?:\d[ -]?){13,16}\b"""),
            placeholder = "[CARD]",
        ),
        Rule(
            kind = "ip-address",
            regex = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b"""),
            placeholder = "[IP]",
        ),
        Rule(
            kind = "phone",
            regex = Regex("""(?<!\w)(?:\+?\d{1,3}[ .-]?)?(?:\(?\d{2,4}\)?[ .-]?){2,4}\d{2,4}(?!\w)"""),
            placeholder = "[PHONE]",
        ),
    )

    fun redact(input: String): Result {
        var working = input
        val findings = mutableListOf<Finding>()
        for (rule in rules) {
            working = rule.regex.replace(working) { match ->
                findings += Finding(rule.kind, match.value)
                rule.placeholder
            }
        }
        return Result(working, findings)
    }
}
