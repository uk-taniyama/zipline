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
import okio.Buffer
import org.junit.After
import org.junit.Test

class JsObjectReaderTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun happyPath() {
    val bytecode: ByteArray = quickJs.compile(
      """
      |function greet(name) {
      |  return "hello, " + name;
      |}
      """.trimMargin(), "hello.js"
    )

    quickJs.execute(bytecode)

    val reader = JsObjectReader(Buffer().write(bytecode))
    val jsObject = reader.readJsObject()
    println(jsObject)
  }
}
