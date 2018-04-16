package de.zalando.zally.util.ast

import io.swagger.parser.SwaggerParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class MethodCallRecorderTest {
    @Test
    fun `with non-null path`() {
        val content = """
            {
              "swagger": "2.0",
              "info": {
                "title": "Things API",
                "description": "Description of things",
                "version": "1.0.0"
              },
              "paths": {
                "/tests": {
                  "get": {
                    "responses": {
                      "200": {
                        "description": "OK"
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val spec = SwaggerParser().parse(content)
        val recorder = MethodCallRecorder(spec)
        val specProxy = recorder.createProxy()
        val description = specProxy.paths?.get("/tests")?.get?.responses?.get("200")?.description

        assertThat(description).isEqualTo("OK")
        assertThat(recorder.pointer).isEqualTo("#/paths/~1tests/get/responses/200/description")
    }

    @Test
    fun `with null path`() {
        val content = """
            {
              "swagger": "2.0",
              "info": {
                "title": "Things API",
                "description": "Description of things",
                "version": "1.0.0"
              },
              "paths": {
                "/tests": {
                  "get": {
                    "responses": {
                      "200": {
                        "description": "OK"
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val spec = SwaggerParser().parse(content)
        val recorder = MethodCallRecorder(spec)
        val specProxy = recorder.createProxy()
        val description = specProxy.paths?.get("/null")?.get?.responses?.get("200")?.description

        assertThat(description).isNull()
        assertThat(recorder.pointer).isEqualTo("#/paths/~1null/get/responses/200/description")
    }
}
