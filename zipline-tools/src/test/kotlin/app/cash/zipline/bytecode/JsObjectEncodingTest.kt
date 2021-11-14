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
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Test

class JsObjectEncodingTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun decodeAndEncode() {
    val evalFunction = assertRoundTrip(
      """
      |function greet(name) {
      |  return "hello, " + name;
      |}
      """.trimMargin(), "hello.js"
    )

    assertThat(evalFunction.name).isEqualTo("<eval>")
    assertThat(evalFunction.debug?.fileName).isEqualTo("hello.js")
    assertThat(evalFunction.debug?.lineNumber).isEqualTo(1)

    val greetFunction = evalFunction.constantPool.single() as JsFunctionBytecode
    assertThat(greetFunction.name).isEqualTo("greet")
    assertThat(greetFunction.argCount).isEqualTo(1)
    assertThat(greetFunction.locals.single().name).isEqualTo("name")
    assertThat(greetFunction.debug?.fileName).isEqualTo("hello.js")
    assertThat(greetFunction.debug?.lineNumber).isEqualTo(1)
  }

  @Test fun primitiveValues() {
    assertRoundTrip(
      """
      |function primitiveValues() {
      |  return [
      |    null,
      |    undefined,
      |    false,
      |    true,
      |    2147483647,
      |    2.7182818284590452354,
      |    "hello"
      |  ];
      |}
      """.trimMargin()
    )
  }

  @Test fun atomsInNames() {
    val evalFunction = assertRoundTrip(
      """
      |function toString() {
      |  return "JSON"
      |}
      """.trimMargin()
    )
    assertThat(evalFunction.name).isEqualTo("<eval>")
    val toStringFunction = evalFunction.constantPool.single() as JsFunctionBytecode
    assertThat(toStringFunction.name).isEqualTo("toString")
  }

  @Test fun kotlinStdlib() {
    val script = FileSystem.SYSTEM.read("/Users/jwilson/Projects/zipline/zipline/build/generated/testingJs/kotlin-kotlin-stdlib-js-ir.js".toPath()) {
      readUtf8()
    }
    assertRoundTrip(script)
  }

  /** Returns the model object for the bytecode of [script]. */
  private fun assertRoundTrip(
    script: String,
    fileName: String = "test.js"
  ): JsFunctionBytecode {
    // Use QuickJS to compile a script into bytecode.
    val bytecode: ByteArray = quickJs.compile(script, fileName)

    // Confirm we can decode the bytecode.
    val reader = JsObjectReader(bytecode)
    val decoded = reader.use {
      reader.readJsObject()
    }

    // Confirm that encoding the model yields the original bytecode.
    val buffer = Buffer()
    JsObjectWriter(reader.atoms, buffer).use { writer ->
      writer.writeJsObject(decoded)
    }
    assertThat(buffer.readByteString()).isEqualTo(bytecode.toByteString())

    // Return the decoded model.
    return decoded as JsFunctionBytecode
  }
}
