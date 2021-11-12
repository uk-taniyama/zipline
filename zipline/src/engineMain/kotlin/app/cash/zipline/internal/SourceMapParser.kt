package app.cash.zipline.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.decodeBase64
import okio.IOException

@Serializable
internal data class SourceMapJson(
  val version: Int,
  val file: String,
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
  val sourcesIndex: String?,
  val sourceLine: Long,
  val sourceColumn: Long,
  val nameIndex: String?
)

data class SourceMap(
  val version: Int,
  val file: String,
  val sourcesContent: List<String?>,
  val groups: List<Group>
)

internal class SourceMapParser {
  fun parse(sourceMapJson: String): SourceMap {
    val sourceMap = Json.decodeFromString(SourceMapJson.serializer(), sourceMapJson)

    val buffer = Buffer()
    val groups = mutableListOf<Group>()
    for (group in sourceMap.mappings.split(";")) {
      val segments = mutableListOf<Segment>()
      for (segment in group.split(",")) {
        buffer.writeUtf8(segment)
        while (!buffer.exhausted()) {
          val startingColumn = buffer.readVarint()
          var source: String? = null
          var sourceLine = -1L
          var sourceColumn = -1L
          var name: String? = null
          if (!buffer.exhausted()) {
            val sourcesIndex = buffer.readVarint()
            source = sourceMap.sources[sourcesIndex.toInt()]
          }
          if (!buffer.exhausted()) {
            sourceLine = buffer.readVarint()
          }
          if (!buffer.exhausted()) {
            sourceColumn = buffer.readVarint()
          }
          if (!buffer.exhausted()) {
            val nameIndex = buffer.readVarint()
            name = sourceMap.names[nameIndex.toInt()]
          }
          segments += Segment(startingColumn, source, sourceLine, sourceColumn, name)
        }
      }
      groups += Group(segments)
    }

    return SourceMap(sourceMap.version, sourceMap.file, sourceMap.sourcesContent, groups)
  }
}

fun BufferedSource.readVarint(): Long {
  var result = 0L
  while (true) {
    val b = readBase64Character()
    result = (b and 0x1F).toLong() or (result shl 5)
    if ((b and 0x20) == 0) {
      return result
    }
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
