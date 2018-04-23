package de.zalando.zally.rule

import de.zalando.zally.util.ast.MethodCallRecorder
import de.zalando.zally.util.ast.ReverseAst
import io.swagger.models.Swagger
import io.swagger.parser.SwaggerParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.converter.SwaggerConverter
import io.swagger.v3.parser.core.models.ParseOptions

class Context(openApi: OpenAPI, swagger: Swagger? = null) {
    private val recorder = MethodCallRecorder(openApi).skipMethods(*extensionNames)
    private val openApiAst = ReverseAst.fromObject(openApi).withExtensionMethodNames(*extensionNames).build()
    private val swaggerAst = swagger?.let { ReverseAst.fromObject(it).build() }

    val api = recorder.proxy

    val currentPointer: String
        get() = recorder.pointer

    fun isIgnored(pointer: String, ruleId: String): Boolean =
        swaggerAst?.isIgnored(pointer, ruleId) ?: openApiAst.isIgnored(pointer, ruleId)

    fun pointerForValue(value: Any): String? = swaggerAst?.getPointer(value) ?: openApiAst.getPointer(value)

    companion object {
        val extensionNames = arrayOf("getVendorExtensions", "getExtensions")

        fun createOpenApiContext(content: String): Context? {
            val parseOptions = ParseOptions()
            parseOptions.isResolve = true
            parseOptions.isResolveFully = true

            return OpenAPIV3Parser().readContents(content, null, parseOptions)?.openAPI?.let {
                Context(it)
            }
        }

        fun createSwaggerContext(content: String): Context? =
            SwaggerParser().readWithInfo(content)?.let {
                val swagger = it.swagger ?: return null
                val openApi = SwaggerConverter().convert(it)?.openAPI

                openApi?.let { Context(openApi, swagger) }
            }
    }
}
