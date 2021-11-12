package app.cash.zipline

import app.cash.zipline.internal.SourceMapParser2
import app.cash.zipline.internal.readVarint
import okio.Path.Companion.toPath
import kotlin.test.Test
import okio.Buffer
import okio.FileSystem

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

    val element = Element("<anonymous>", "demo.js", 14)

    val sourceMap = SourceMapParser2().parse(sourceMapJson)
    val segmentInGroup = sourceMap.find(element.lineNumber)
    println("     at ${element.line} ${segmentInGroup?.source ?: element.fileName}:${segmentInGroup?.sourceLine}")
  }

  @Test fun readVarints() {
    val buffer = Buffer().writeUtf8("IAkpZI")
    println(buffer.readVarint())
    println(buffer.readVarint())
    println(buffer.readVarint())
    println(buffer.readVarint())
    println(buffer.readVarint())
  }

  @Test fun mapStacktrace() {
    val stackTrace = listOf(
      Element("captureStack", "./kotlin-kotlin-stdlib-js-ir.js", 20884),
      Element("IllegalStateException_init_Create_0", "./kotlin-kotlin-stdlib-js-ir.js", 23220),
      Element("goBoom1", "./zipline-root-testing.js", 821),
      Element("goBoom2", "./zipline-root-testing.js", 818),
      Element("goBoom3", "./zipline-root-testing.js", 815),
      Element("<anonymous>", "./zipline-root-testing.js", 826),
      Element("<anonymous>", "./zipline-root-testing.js", 1088),
      Element("<anonymous>", "./zipline-root-zipline.js", 1130),
      Element("<anonymous>", "./zipline-root-zipline.js", 1145),
    )

    val root = "/Users/jwilson/Projects/zipline/zipline/testing/build/compileSync/main/developmentLibrary/kotlin".toPath()
    for (element in stackTrace) {
      val sourceMap = SourceMapParser2().parse(fileSystem.read(root / "${element.fileName}.map") { readUtf8() })
      val segmentInGroup = sourceMap.find(element.lineNumber)
      println("     at ${element.line} ${segmentInGroup?.source ?: element.fileName}:${segmentInGroup?.sourceLine}")
    }
  }

  class Element(
    val line: String,
    val fileName: String,
    val lineNumber: Int
  )
}

val fileSystem: FileSystem = FileSystem.SYSTEM
