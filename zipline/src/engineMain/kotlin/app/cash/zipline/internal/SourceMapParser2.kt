package app.cash.zipline.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSource
import okio.IOException

@Serializable
internal data class SourceMapJson(
  val version: Int,
  val file: String? = null,
  val sources: List<String>,
  val sourcesContent: List<String?>,
  val names: List<String>,
  val mappings: String,
)

data class Group(
  val segments: List<Segment>
)

data class Segment(
  val startingColumn: Long,
  val source: String?,
  val sourceLine: Long,
  val sourceColumn: Long,
  val name: String?
)

data class SourceMap(
  val version: Int,
  val file: String?,
  val sourcesContent: List<String?>,
  val groups: List<Group>
) {
  fun find(lineNumber: Int): Segment? {
    return groups[lineNumber - 1].segments.firstOrNull()
  }
}

internal class SourceMapParser2 {
  fun parse(sourceMapJson: String): SourceMap {
    val sourceMap = Json.decodeFromString(SourceMapJson.serializer(), sourceMapJson)

    val buffer = Buffer()

    var sourceIndex = 0L
    var sourceLine = 0L
    var sourceColumn = 0L
    var nameIndex = 0L

    val groups = mutableListOf<Group>()
    for (group in sourceMap.mappings.split(";")) {
      var startingColumn = 0L

      val segments = mutableListOf<Segment>()
      for (segment in group.split(",")) {
        if (segment.isEmpty()) continue
        buffer.writeUtf8(segment)

        startingColumn += buffer.readVarint()

        // Read an optional updated source file, column, and row.
        if (!buffer.exhausted()) {
          sourceIndex += buffer.readVarint()
          sourceLine += buffer.readVarint()
          sourceColumn += buffer.readVarint()
        }

        // Read an optional name.
        if (!buffer.exhausted()) {
          nameIndex += buffer.readVarint()
        }

        if (!buffer.exhausted()) {
          throw IOException("unexpected part: $segment")
        }

        check(sourceIndex >= -1)
        check(nameIndex >= -1)
        check(sourceLine >= -1) {
          "unexpected source line: $sourceLine"
        }
        check(sourceColumn >= -1)

        val source = if (sourceIndex >= 0 && sourceIndex < sourceMap.sources.size) sourceMap.sources[sourceIndex.toInt()] else null
        val name = if (nameIndex >= 0 && nameIndex < sourceMap.names.size) sourceMap.names[nameIndex.toInt()] else null
        segments += Segment(startingColumn + 1, source, sourceLine + 1, sourceColumn + 1, name)
      }

      groups += Group(segments)
    }

    return SourceMap(sourceMap.version, sourceMap.file, sourceMap.sourcesContent, groups)
  }
}

fun BufferedSource.readVarint(): Long {
  var shift = 0
  var result = 0L
  while (true) {
    val b = readBase64Character()
    result += (b and 0x1F) shl shift
    if ((b and 0x20) == 0) {
      val unsigned = result ushr 1
      return when {
        (result and 0x1) == 0x1L -> -unsigned
        else -> unsigned
      }
    }
    shift += 5
  }
  throw IOException("malformed varint")
}

fun BufferedSource.readBase64Character(): Int {
  val c = readByte().toInt().toChar()
  if (c in 'A'..'Z') {
    // char ASCII value
    //  A    65    0
    //  Z    90    25 (ASCII - 65)
    return c.code - 65
  } else if (c in 'a'..'z') {
    // char ASCII value
    //  a    97    26
    //  z    122   51 (ASCII - 71)
    return c.code - 71
  } else if (c in '0'..'9') {
    // char ASCII value
    //  0    48    52
    //  9    57    61 (ASCII + 4)
    return c.code + 4
  } else if (c == '+' || c == '-') {
    return 62
  } else if (c == '/' || c == '_') {
    return 63
  } else {
    throw IOException("Unexpected character")
  }
}

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.and(other: Int): Int = toInt() and other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.shl(other: Int): Int = toInt() shl other
