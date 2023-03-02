/*
 * Copyright (c) 2023.
 *
 * This file is part of xmlutil.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package nl.adaptivity.xmlutil.core.impl.multiplatform

import kotlinx.cinterop.*
import platform.posix.size_t

public class FileWriter(public val outStream: FileOutputStream) : Writer(), Closeable {

    public fun appendCodePoint(codepoint: Int): FileWriter {
        memScoped {
            val buffer = allocArray<UByteVar>(4)

            val byteCount: size_t = addCodepointToBuffer(buffer, codepoint)
            if (outStream.writePtr(buffer, byteCount) != byteCount) error("Failure to write full character")
        }
        return this
    }

    private fun addCodepointToBuffer(
        buffer: CArrayPointer<UByteVar>,
        codepoint: Int
    ): size_t {
        val byteCount: size_t
        when {
            codepoint <= 0x7f -> {
                buffer[0] = codepoint.toUByte()
                byteCount = 1u
            }

            codepoint <= 0x7ff -> {
                buffer[0] = (0xC0 or (codepoint shr 6)).toUByte()
                buffer[1] = (0x80 or (codepoint and 0x3f)).toUByte()
                byteCount = 2u
            }

            codepoint < 0xffff -> {
                buffer[0] = (0xE0 or (codepoint shr 12)).toUByte()
                buffer[1] = (0x80 or ((codepoint shr 6) and 0x3f)).toUByte()
                buffer[2] = (0x80 or (codepoint and 0x3f)).toUByte()
                byteCount = 3u
            }

            else -> {
                println("Codepoint: $codepoint")
                buffer[0] = (0xF0 or (codepoint shr 18)).toUByte()
                buffer[1] = (0x80 or ((codepoint shr 12) and 0x3f)).toUByte()
                buffer[2] = (0x80 or ((codepoint shr 6) and 0x3f)).toUByte()
                buffer[3] = (0x80 or (codepoint and 0x3f)).toUByte()
                byteCount = 4u
            }
        }
        return byteCount
    }


    override fun append(value: Char): FileWriter {
        return appendCodePoint(value.code)
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): FileWriter {
        if (value == null) {
            return append("null", 0, 4)
        }
        var inPos = startIndex
        memScoped {
            val outBuffer = allocArray<UByteVar>(BUFFER_SIZE)
            var outPos = 0
            while (inPos < endIndex) {
                if (outPos > MAX_OUT_POS) {
                    outStream.writeAllPtr(outBuffer, outPos)
                    outPos = 0
                }
                val codepoint: Int
                val char = value[inPos]
                if (char.isHighSurrogate()) {
                    require(inPos + 1 < endIndex) { "Surrogate pair beyond end Index" }
                    val high = (char.code and 0x3ff) shl 10
                    val low = value[inPos + 1].code and 0x3ff
                    codepoint = 0x10000 + high + low
                    inPos += 2
                } else {
                    codepoint = char.code
                    inPos++
                }
                val bytes = addCodepointToBuffer((outBuffer + outPos)!!, codepoint)
                outPos += bytes.toInt()
            }

            if (outPos > 0) {
                outStream.writeAllPtr(outBuffer, outPos)
            }
        }

        return this
    }

    override fun write(text: String) {
        write(text, 0, text.length)
    }

    public fun write(text: String, begin: Int, length: Int) {
        append(text, begin, begin + length)
    }

    override fun close() {
        outStream.close()
    }
}

private const val BUFFER_SIZE = 0x2004 // 8KB + 4 bytes extra
private const val MAX_OUT_POS = BUFFER_SIZE - 4 // We require at least 4 bytes space for expansion
