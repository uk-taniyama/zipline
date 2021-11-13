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
import okio.ByteString
import okio.IOException

class QuickJsObjectReader(
  private val source: BufferedSource,
  private val JS_WRITE_OBJ_BYTECODE: Boolean = true,
  private val JS_WRITE_OBJ_REFERENCE: Boolean = true,
) {
  private var firstAtom = 1

  private lateinit var atoms: List<String>

  /*
typedef struct BCWriterState {
    JSContext *ctx;
    DynBuf dbuf;
    BOOL byte_swap : 8;
    BOOL allow_bytecode : 8;
    BOOL allow_sab : 8;
    BOOL allow_reference : 8;
    uint32_t first_atom;
    uint32_t *atom_to_idx;
    int atom_to_idx_size;
    JSAtom *idx_to_atom;
    int idx_to_atom_count;
    int idx_to_atom_size;
    uint8_t **sab_tab;
    int sab_tab_len;
    int sab_tab_size;
    /* list of referenced objects (used if allow_reference = TRUE) */
    JSObjectList object_list;
} BCWriterState;
   */

  // JS_WriteObject2
  fun readObject(): JsObject {
    check(!::atoms.isInitialized)
    atoms = readObjectAtoms()
    return readObjectRec()
  }

  // JS_WriteObjectAtoms
  private fun readObjectAtoms(): List<String> {
    val version = source.readByte().toInt()
    if (version != version) {
      throw IOException("unexpected version (expected $BC_VERSION)")
    }
    val atomCount = readLeb128()
    val result = mutableListOf<String>()
    for (i in 0 until atomCount) {
      result += readJsString()
    }
    return result
  }

  // JS_WriteObjectRec
  private fun readObjectRec(): JsObject {
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
    var headerRefCount = 1
    val flags = source.readShort().toInt()

    val has_prototype = flags.bitToBoolean(0)
    val has_simple_parameter_list = flags.bitToBoolean(1)
    val is_derived_class_constructor = flags.bitToBoolean(2)
    val need_home_object = flags.bitToBoolean(3)
    val func_kind = flags.bitsToInt(4, 2)
    val new_target_allowed = flags.bitToBoolean(6)
    val super_call_allowed = flags.bitToBoolean(7)
    val super_allowed = flags.bitToBoolean(8)
    val arguments_allowed = flags.bitToBoolean(9)
    val hasDebug = flags.bitToBoolean(10)
    val backtrace_barrier = flags.bitToBoolean(11)

    val jsMode = source.readByte()
    val funcName = readAtom()
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

    val debug: Debug? = if (hasDebug) readDebug() else null

    val constantPool = mutableListOf<JsObject>()
    for (i in 0 until constantPoolCount) {
      constantPool += readObjectRec()
    }

    val result = JsFunctionBytecode(
      flags = flags.toShort(),
      jsMode = jsMode,
      funcName = funcName,
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
    return result
  }

  private fun readAtom(): JsAtom {
    val valueAndType = readLeb128()
    val value = valueAndType shr 1
    if (valueAndType and 0x1 == 0x1) return JsAtom.AtomInt(value)

    if (value < BUILT_IN_ATOMS.size) {
      return JsAtom.AtomString(value, BUILT_IN_ATOMS[value])
    }

    return JsAtom.AtomString(value, atoms[value - BUILT_IN_ATOMS.size])
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

  private companion object {
    const val BC_VERSION = 1
    const val BC_TAG_NULL = 1
    const val BC_TAG_UNDEFINED = 2
    const val BC_TAG_BOOL_FALSE = 3
    const val BC_TAG_BOOL_TRUE = 4
    const val BC_TAG_INT32 = 5
    const val BC_TAG_FLOAT64 = 6
    const val BC_TAG_STRING = 7
    const val BC_TAG_OBJECT = 8
    const val BC_TAG_ARRAY = 9
    const val BC_TAG_BIG_INT = 10
    const val BC_TAG_BIG_FLOAT = 11
    const val BC_TAG_BIG_DECIMAL = 12
    const val BC_TAG_TEMPLATE_OBJECT = 13
    const val BC_TAG_FUNCTION_BYTECODE = 14
    const val BC_TAG_MODULE = 15
    const val BC_TAG_TYPED_ARRAY = 16
    const val BC_TAG_ARRAY_BUFFER = 17
    const val BC_TAG_SHARED_ARRAY_BUFFER = 18
    const val BC_TAG_DATE = 19
    const val BC_TAG_OBJECT_VALUE = 20
    const val BC_TAG_OBJECT_REFERENCE = 21

    /** This is computed dynamically at QuickJS boot, and depends on build flags. */
    val BUILT_IN_ATOMS = listOf(
      "",
      "null",
      "false",
      "true",
      "if",
      "else",
      "return",
      "var",
      "this",
      "delete",
      "void",
      "typeof",
      "new",
      "in",
      "instanceof",
      "do",
      "while",
      "for",
      "break",
      "continue",
      "switch",
      "case",
      "default",
      "throw",
      "try",
      "catch",
      "finally",
      "function",
      "debugger",
      "with",
      "class",
      "const",
      "enum",
      "export",
      "extends",
      "import",
      "super",
      "implements",
      "interface",
      "let",
      "package",
      "private",
      "protected",
      "public",
      "static",
      "yield",
      "await",
      "",
      "length",
      "fileName",
      "lineNumber",
      "message",
      "errors",
      "stack",
      "name",
      "toString",
      "toLocaleString",
      "valueOf",
      "eval",
      "prototype",
      "constructor",
      "configurable",
      "writable",
      "enumerable",
      "value",
      "get",
      "set",
      "of",
      "__proto__",
      "undefined",
      "number",
      "boolean",
      "string",
      "object",
      "symbol",
      "integer",
      "unknown",
      "arguments",
      "callee",
      "caller",
      "<eval>",
      "<ret>",
      "<var>",
      "<arg_var>",
      "<with>",
      "lastIndex",
      "target",
      "index",
      "input",
      "defineProperties",
      "apply",
      "join",
      "concat",
      "split",
      "construct",
      "getPrototypeOf",
      "setPrototypeOf",
      "isExtensible",
      "preventExtensions",
      "has",
      "deleteProperty",
      "defineProperty",
      "getOwnPropertyDescriptor",
      "ownKeys",
      "add",
      "done",
      "next",
      "values",
      "source",
      "flags",
      "global",
      "unicode",
      "raw",
      "new.target",
      "this.active_func",
      "<home_object>",
      "<computed_field>",
      "<static_computed_field>",
      "<class_fields_init>",
      "<brand>",
      "#constructor",
      "as",
      "from",
      "meta",
      "*default*",
      "*",
      "Module",
      "then",
      "resolve",
      "reject",
      "promise",
      "proxy",
      "revoke",
      "async",
      "exec",
      "groups",
      "status",
      "reason",
      "globalThis",
      "not-equal",
      "timed-out",
      "ok",
      "toJSON",
      "Object",
      "Array",
      "Error",
      "Number",
      "String",
      "Boolean",
      "Symbol",
      "Arguments",
      "Math",
      "JSON",
      "Date",
      "Function",
      "GeneratorFunction",
      "ForInIterator",
      "RegExp",
      "ArrayBuffer",
      "SharedArrayBuffer",
      "Uint8ClampedArray",
      "Int8Array",
      "Uint8Array",
      "Int16Array",
      "Uint16Array",
      "Int32Array",
      "Uint32Array",
      "Float32Array",
      "Float64Array",
      "DataView",
      "Map",
      "Set",
      "WeakMap",
      "WeakSet",
      "Map Iterator",
      "Set Iterator",
      "Array Iterator",
      "String Iterator",
      "RegExp String Iterator",
      "Generator",
      "Proxy",
      "Promise",
      "PromiseResolveFunction",
      "PromiseRejectFunction",
      "AsyncFunction",
      "AsyncFunctionResolve",
      "AsyncFunctionReject",
      "AsyncGeneratorFunction",
      "AsyncGenerator",
      "EvalError",
      "RangeError",
      "ReferenceError",
      "SyntaxError",
      "TypeError",
      "URIError",
      "InternalError",
      "<brand>",  // Symbols
      "Symbol.toPrimitive",  // Symbols
      "Symbol.iterator",  // Symbols
      "Symbol.match",  // Symbols
      "Symbol.matchAll",  // Symbols
      "Symbol.replace",  // Symbols
      "Symbol.search",  // Symbols
      "Symbol.split",  // Symbols
      "Symbol.toStringTag",  // Symbols
      "Symbol.isConcatSpreadable",  // Symbols
      "Symbol.hasInstance",  // Symbols
      "Symbol.species",  // Symbols
      "Symbol.unscopables",  // Symbols
      "Symbol.asyncIterator",  // Symbols
    )
  }
}

class JsVarDef(
  val varName: JsAtom,
  val scopeLevel: Int,
  val scopeNext: Int,
  val varKind: Int, // JsVarKindEnum
  val isConst: Boolean,
  val isLexical: Boolean,
  val isCaptured: Boolean,
)

class JsClosureVar(
  val varName: JsAtom,
  val varIndex: Int,
  val isLocal: Boolean,
  val isArg: Boolean,
  val isConst: Boolean,
  val isLexical: Boolean,
  val varKind: Int, // JsVarKindEnum
)

class Debug(
  val fileName: JsAtom,
  val lineNumber: Int,
  val pc2Line: ByteString,
)

sealed class JsAtom {
  data class AtomInt(val value: Int) : JsAtom()
  data class AtomString(val index: Int, val value: String) : JsAtom()
}

sealed class JsObject
object JsNull : JsObject()
object JsUndefined : JsObject()
data class JsBoolean(val value: Boolean) : JsObject()
data class JsInt(val value: Int) : JsObject()
data class JsDouble(val value: Double) : JsObject()
data class JsString(val value: String) : JsObject()
data class JsFunctionBytecode(
  val flags: Short,
  val jsMode: Byte,
  val funcName: JsAtom,
  val argCount: Int,
  val varCount: Int,
  val definedArgCount: Int,
  val stackSize: Int,
  val locals: List<JsVarDef>,
  val closureVars: List<JsClosureVar>,
  val bytecode: ByteString,
  val constantPool: List<JsObject>,
  val debug: Debug?,
) : JsObject()

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.and(other: Int): Int = toInt() and other

/** Like QuickJS' `bc_get_flags` where n is 1. */
@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun Int.bitToBoolean(bit: Int): Boolean {
  return (this shr bit) and 0x1 != 0x1
}

/** Like QuickJS' `bc_get_flags` where n is > 1. */
@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun Int.bitsToInt(bit: Int, bitCount: Int): Int {
  return (this shr bit) and ((1 shr bitCount) - 1)
}
