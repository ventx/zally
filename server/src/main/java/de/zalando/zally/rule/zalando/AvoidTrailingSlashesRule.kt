package de.zalando.zally.rule.zalando

import de.zalando.zally.rule.AbstractRule
import de.zalando.zally.rule.api.Check
import de.zalando.zally.rule.api.Severity
import de.zalando.zally.rule.api.Violation
import de.zalando.zally.util.PatternUtil
import io.swagger.models.Swagger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AvoidTrailingSlashesRule(@Autowired ruleSet: ZalandoRuleSet) : AbstractRule(ruleSet) {
    override val title = "Avoid Trailing Slashes"
    override val id = "136"
    override val severity = Severity.MUST
    private val DESCRIPTION = "Rule avoid trailing slashes is not followed"

    @Check(severity = Severity.MUST)
    fun validate(swagger: Swagger): Violation? {
        val paths = swagger.paths.orEmpty().keys.filter { it != null && PatternUtil.hasTrailingSlash(it) }
        return if (!paths.isEmpty()) Violation(DESCRIPTION, paths) else null
    }
}