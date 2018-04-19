package de.zalando.zally.rule.zalando

import de.zalando.zally.rule.Context
import de.zalando.zally.rule.api.Check
import de.zalando.zally.rule.api.Rule
import de.zalando.zally.rule.api.Severity
import de.zalando.zally.rule.api.Violation
import io.swagger.models.Swagger

@Rule(
    ruleSet = ZalandoRuleSet::class,
    id = "215",
    severity = Severity.SHOULD,
    title = "Provide API Identifier"
)
class ApiIdentifierRule {
    private val apiIdPattern = """^[a-z0-9][a-z0-9-:.]{6,62}[a-z0-9]$""".toRegex()

    private val noApiIdDesc = "API Identifier should be provided"
    private val invalidApiIdDesc = "API Identifier doesn't match the pattern $apiIdPattern"

    private val extensionName = "x-api-id"

    @Check(severity = Severity.SHOULD)
    fun validate(context: Context): Violation? {
        val apiId = context.api.info?.extensions?.get(extensionName)

        return when {
            apiId == null || apiId !is String -> Violation(noApiIdDesc, context.currentPointer)
            !apiId.matches(apiIdPattern) -> Violation(invalidApiIdDesc, context.currentPointer)
            else -> null
        }
    }
}
