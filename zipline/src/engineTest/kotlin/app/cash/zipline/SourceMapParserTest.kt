package app.cash.zipline

import app.cash.zipline.internal.SourceMapParser
import app.cash.zipline.internal.readVarint
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex

class SourceMapParserTest {
  @Test fun simpleSourceMap() {
    val sourceMapJson = """
      {
        "version": 3,
        "file": "kotlin-js-demo.js",
        "sources": [
          "../../../../../src/main/kotlin/main.kt"
        ],
        "sourcesContent": [
          null
        ],
        "names": [],
        "mappings": ";;;;;;;;;;;;EAAA,gB;IACE,YAAY,c;IACZ,OAAQ,KAAI,KAAJ,C;EACV,C;;;;;;"
      }
      """.trimIndent()

    val sourceMap = SourceMapParser().parse(sourceMapJson)
    println(sourceMap)
  }
}

