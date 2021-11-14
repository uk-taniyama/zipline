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

import app.cash.zipline.QuickJs
import com.google.common.truth.Truth.assertThat
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Test

class JsObjectEncodingTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun decodeAndEncode() {
    val bytecode: ByteArray = quickJs.compile(
      """
      |function greet(name) {
      |  return "hello, " + name;
      |}
      """.trimMargin(), "hello.js"
    )

    quickJs.execute(bytecode)

    val reader = JsObjectReader(bytecode)
    val evalFunction = reader.readJsObject() as JsFunctionBytecode
    assertThat(evalFunction.name).isEqualTo("<eval>")
    assertThat(evalFunction.debug?.fileName).isEqualTo("hello.js")
    assertThat(evalFunction.debug?.lineNumber).isEqualTo(1)

    val greetFunction = evalFunction.constantPool.single() as JsFunctionBytecode
    assertThat(greetFunction.name).isEqualTo("greet")
    assertThat(greetFunction.argCount).isEqualTo(1)
    assertThat(greetFunction.locals.single().name).isEqualTo("name")
    assertThat(greetFunction.debug?.fileName).isEqualTo("hello.js")
    assertThat(greetFunction.debug?.lineNumber).isEqualTo(1)

    // Confirm we encode back to the original bytes. To get byte-for-byte equality we must use the
    // reader's AtomSet.
    assertThat(evalFunction.encode(reader.atoms)).isEqualTo(bytecode.toByteString())
  }

  private fun JsObject.encode(atoms: AtomSet): ByteString {
    val buffer = Buffer()
    JsObjectWriter(atoms, buffer)
      .writeJsObject(this)
    return buffer.readByteString()
  }
}
