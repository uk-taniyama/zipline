/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.bytecode

import okio.BufferedSource
import okio.IOException

class JsObjectReader(
  private val source: BufferedSource,
  private val JS_WRITE_OBJ_BYTECODE: Boolean = true,
  private val JS_WRITE_OBJ_REFERENCE: Boolean = true,
) {
  private lateinit var atoms: AtomSet

  fun readJsObject(): JsObject {
    check(!::atoms.isInitialized)
    atoms = readObjectAtoms()
    return readObjectRecursive()
  }

  private fun readObjectAtoms(): AtomSet {
    val version = source.readByte().toInt()
    if (version != version) {
      throw IOException("unexpected version (expected $BC_VERSION)")
    }
    val atomCount = readLeb128()
    val result = mutableListOf<String>()
    for (i in 0 until atomCount) {
      result += readJsString()
    }
    return AtomSet(result)
  }

  private fun readObjectRecursive(): JsObject {
    return when (val tag = source.readByte().toInt()) {
      BC_TAG_NULL -> JsNull
      BC_TAG_UNDEFINED -> JsUndefined
      BC_TAG_BOOL_FALSE -> JsBoolean(false)
      BC_TAG_BOOL_TRUE -> JsBoolean(true)
      BC_TAG_INT32 -> JsInt(readSleb128())
      BC_TAG_FLOAT64 -> JsDouble(Double.fromBits(source.readLong()))
      BC_TAG_STRING -> JsString(readJsString())
      BC_TAG_OBJECT -> TODO("BC_TAG_OBJECT")
      BC_TAG_ARRAY -> TODO("BC_TAG_ARRAY")
      BC_TAG_BIG_INT -> TODO("BC_TAG_BIG_INT")
      BC_TAG_BIG_FLOAT -> TODO("BC_TAG_BIG_FLOAT")
      BC_TAG_BIG_DECIMAL -> TODO("BC_TAG_BIG_DECIMAL")
      BC_TAG_TEMPLATE_OBJECT -> TODO("BC_TAG_TEMPLATE_OBJECT")
      BC_TAG_FUNCTION_BYTECODE -> readFunction()
      BC_TAG_MODULE -> TODO("BC_TAG_MODULE")
      BC_TAG_TYPED_ARRAY -> TODO("BC_TAG_TYPED_ARRAY")
      BC_TAG_ARRAY_BUFFER -> TODO("BC_TAG_ARRAY_BUFFER")
      BC_TAG_SHARED_ARRAY_BUFFER -> TODO("BC_TAG_SHARED_ARRAY_BUFFER")
      BC_TAG_DATE -> TODO("BC_TAG_DATE")
      BC_TAG_OBJECT_VALUE -> TODO("BC_TAG_OBJECT_VALUE")
      BC_TAG_OBJECT_REFERENCE -> TODO("BC_TAG_OBJECT_REFERENCE")
      else -> throw IOException("unexpected tag: $tag")
    }
  }

  private fun readFunction(): JsFunctionBytecode {
    val flags = source.readShort().toInt()
    val jsMode = source.readByte()
    val functionName = readAtom()
    val argCount = readLeb128()
    val varCount = readLeb128()
    val definedArgCount = readLeb128()
    val stackSize = readLeb128()
    val closureVarCount = readLeb128()
    val constantPoolCount = readLeb128()
    val bytecodeLength = readLeb128()
    val localCount = readLeb128()

    val locals = mutableListOf<JsVarDef>()
    for (i in 0 until localCount) {
      locals += readVarDef()
    }

    val closureVars = mutableListOf<JsClosureVar>()
    for (i in 0 until closureVarCount) {
      closureVars += readClosureVar()
    }

    val bytecode = source.readByteString(bytecodeLength.toLong())
    // TODO: fixup atoms within bytecode?

    val hasDebug = flags.bitToBoolean(10)
    val debug: Debug? = if (hasDebug) readDebug() else null

    val constantPool = mutableListOf<JsObject>()
    for (i in 0 until constantPoolCount) {
      constantPool += readObjectRecursive()
    }

    return JsFunctionBytecode(
      flags = flags,
      jsMode = jsMode,
      funcName = functionName,
      argCount = argCount,
      varCount = varCount,
      definedArgCount = definedArgCount,
      stackSize = stackSize,
      locals = locals,
      closureVars = closureVars,
      bytecode = bytecode,
      constantPool = constantPool,
      debug = debug
    )
  }

  private fun readAtom(): JsAtom {
    val valueAndType = readLeb128()
    val value = valueAndType shr 1
    if (valueAndType and 0x1 == 0x1) return JsAtomInt(value)
    return atoms.get(value)
  }

  private fun readVarDef(): JsVarDef {
    val name = readAtom()
    val scopeLevel = readLeb128()
    val scopeNext = readLeb128() - 1
    val flags = source.readByte().toInt()
    return JsVarDef(
      varName = name,
      scopeLevel = scopeLevel,
      scopeNext = scopeNext,
      varKind = flags.bitsToInt(0, 4),
      isConst = flags.bitToBoolean(4),
      isLexical = flags.bitToBoolean(5),
      isCaptured = flags.bitToBoolean(6),
    )
  }

  private fun readClosureVar(): JsClosureVar {
    val name = readAtom()
    val varIndex = readLeb128()
    val flags = source.readByte().toInt()
    return JsClosureVar(
      varName = name,
      varIndex = varIndex,
      isLocal = flags.bitToBoolean(0),
      isArg = flags.bitToBoolean(1),
      isConst = flags.bitToBoolean(2),
      isLexical = flags.bitToBoolean(3),
      varKind = flags.bitsToInt(4, 4),
    )
  }

  private fun readDebug(): Debug {
    val fileName = readAtom()
    val lineNumber = readLeb128()
    val pc2lineLength = readLeb128()
    val pc2line = source.readByteString(pc2lineLength.toLong())
    return Debug(
      fileName = fileName,
      lineNumber = lineNumber,
      pc2Line = pc2line
    )
  }

  private fun readJsString(): String {
    val lengthAndType = readLeb128()
    val isWideChar = lengthAndType and 0x1
    val byteCount = lengthAndType shr 1
    return source.readUtf8(byteCount.toLong())
  }

  private fun readSleb128(): Int {
    val magnitudeAndSign = readLeb128()
    val magnitude = magnitudeAndSign ushr 1
    return when {
      magnitudeAndSign and 0x1 == 0x1 -> -magnitude
      else -> magnitude
    }
  }

  private fun readLeb128(): Int {
    var result = 0
    for (shift in 0 until 32 step 7) {
      val b = source.readByte() and 0xff
      result = result or ((b and 0x7f) shl shift)
      if (b and 0x80 == 0) return result
    }
    throw IOException("unexpected leb128 value")
  }
}
