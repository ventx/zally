package de.zalando.zally.rule.zalando

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.io.Resources
import com.typesafe.config.Config
import de.zalando.zally.rule.Context
import de.zalando.zally.rule.JsonSchemaValidator
import de.zalando.zally.rule.JsonSchemaValidator.ValidationMessage
import de.zalando.zally.rule.ObjectTreeReader
import de.zalando.zally.rule.api.Check
import de.zalando.zally.rule.api.Rule
import de.zalando.zally.rule.api.Severity
import de.zalando.zally.rule.api.Violation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import org.springframework.beans.factory.annotation.Autowired

@Rule(
    ruleSet = ZalandoRuleSet::class,
    id = "150",
    severity = Severity.MUST,
    title = "Use Specific HTTP Status Codes"
)
class UseSpecificHttpStatusCodes(@Autowired rulesConfig: Config) {

    private val allowedStatusCodes = rulesConfig
        .getConfig("${javaClass.simpleName}.allowed_codes")
        .entrySet()
        .map { (key, config) -> (key to config.unwrapped() as List<String>) }.toMap()

    private val objectMapper by lazy { ObjectMapper() }

    private val problemSchemaValidator by lazy {
        val schemaUrl = Resources.getResource("schemas/problem-meta-schema.json")
        val json = ObjectTreeReader().read(schemaUrl)
        JsonSchemaValidator("Problem", json)
    }

    @Check(severity = Severity.MUST)
    fun allowOnlySpecificStatusCodes(context: Context): List<Violation> {
        return context.api.paths.orEmpty().flatMap { (_, pathItem) ->
            pathItem.readOperationsMap().orEmpty().flatMap { (method, operation) ->
                operation.responses.filterNot { (statusCode, _) ->
                    isAllowed(method, statusCode)
                }.map { (_, response) ->
                    response
                }
            }.map {
                val pointer = context.pointerForValue(it) ?: context.currentPointer
                Violation("Operations should use specific HTTP status codes", pointer)
            }
        }
    }

    @Check(severity = Severity.MUST)
    fun defaultResponseMustUseProblemType(context: Context): List<Violation> {
        return context.api.paths.orEmpty().flatMap { (_, pathItem) ->
            pathItem.readOperationsMap().orEmpty()
                .map { (_, operation) -> operation.responses["default"] }
                .filterNotNull()
                .flatMap { testForProblemSchema(it) }
                .map { (schema, validation) ->
                    val pointer = (context.pointerForValue(schema) ?: "#") + validation.path
                    Violation("Default responses require the Problem type: ${validation.message}", pointer)
                }
        }
    }

    private fun isAllowed(method: PathItem.HttpMethod, statusCode: String) =
        allowedStatusCodes[method.name.toLowerCase()].orEmpty().contains(statusCode) ||
            allowedStatusCodes["all"].orEmpty().contains(statusCode)

    private fun testForProblemSchema(response: ApiResponse): List<Pair<Schema<*>, ValidationMessage>> =
        response.content?.flatMap { (_, type) ->
            val node = objectMapper.convertValue(type.schema, JsonNode::class.java)
            val result = problemSchemaValidator.validate(node)
            result.messages.map { Pair(type.schema, it) }
        } ?: emptyList()
}
