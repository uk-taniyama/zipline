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

import com.google.common.truth.Truth.assertThat
import okio.Buffer
import org.junit.Test

class PrimitiveEncodingTest {
  @Test fun leb128() {
    assertRoundTripLeb128(0)
    assertRoundTripLeb128(1)
    assertRoundTripLeb128(127)
    assertRoundTripLeb128(128)
    assertRoundTripLeb128(255)
    assertRoundTripLeb128(256)
    assertRoundTripLeb128(0x40000000)
    assertRoundTripLeb128(0x7fffffff) // MAX_VALUE.

    // These negative ints are interpreted as unsigned.
    assertRoundTripLeb128(-0x80000000) // MIN_VALUE.
    assertRoundTripLeb128(-0x40000000)
    assertRoundTripLeb128(-1) // Max unsigned.
  }

  @Test fun sleb128() {
    assertRoundTripSleb128(0)
    assertRoundTripSleb128(1)
    assertRoundTripSleb128(63)
    assertRoundTripSleb128(64)
    assertRoundTripSleb128(127)
    assertRoundTripSleb128(128)
    assertRoundTripSleb128(255)
    assertRoundTripSleb128(256)
    assertRoundTripSleb128(0x40000000)
    assertRoundTripSleb128(0x7fffffff) // MAX_VALUE.
    assertRoundTripSleb128(-0x80000000) // MIN_VALUE.
    assertRoundTripSleb128(-0x40000000)
    assertRoundTripSleb128(-128)
    assertRoundTripSleb128(-127)
    assertRoundTripSleb128(-64)
    assertRoundTripSleb128(-63)
    assertRoundTripSleb128(-1)
  }

  private fun assertRoundTripLeb128(value: Int) {
    val buffer = Buffer()
    buffer.writeLeb128(value)
    buffer.writeUtf8("x") // Confirm the read doesn't consume too much.
    assertThat(buffer.readLeb128()).isEqualTo(value)
    assertThat(buffer.readUtf8()).isEqualTo("x")
  }

  private fun assertRoundTripSleb128(value: Int) {
    val buffer = Buffer()
    buffer.writeSleb128(value)
    buffer.writeUtf8("x") // Confirm the read doesn't consume too much.
    assertThat(buffer.readSleb128()).isEqualTo(value)
    assertThat(buffer.readUtf8()).isEqualTo("x")
  }
}
