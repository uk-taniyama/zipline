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

import okio.ByteString

sealed class JsObject

object JsNull : JsObject()

object JsUndefined : JsObject()

data class JsBoolean(val value: Boolean) : JsObject()

data class JsInt(val value: Int) : JsObject()

data class JsDouble(val value: Double) : JsObject()

data class JsString(val value: String) : JsObject()

data class JsFunctionBytecode(
  val flags: Int,
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
) : JsObject() {
  val hasPrototype get() = flags.bitToBoolean(0)
  val hasSimpleParameterList get() = flags.bitToBoolean(1)
  val isDerivedClassConstructor get() = flags.bitToBoolean(2)
  val needHomeObject get() = flags.bitToBoolean(3)
  val funcKind get() = flags.bitsToInt(4, 2)
  val newTargetAllowed get() = flags.bitToBoolean(6)
  val superCallAllowed get() = flags.bitToBoolean(7)
  val superAllowed get() = flags.bitToBoolean(8)
  val argumentsAllowed get() = flags.bitToBoolean(9)
  val hasDebug get() = flags.bitToBoolean(10)
  val backtraceBarrier get() = flags.bitToBoolean(11)
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

sealed class JsAtom

data class JsAtomInt(val value: Int) : JsAtom()

data class JsAtomString(val index: Int, val value: String) : JsAtom()

