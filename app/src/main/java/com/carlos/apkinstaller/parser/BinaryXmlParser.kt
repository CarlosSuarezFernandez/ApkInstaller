package com.carlos.apkinstaller.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal parser for Android's compiled binary XML (AXML) that only reads the
 * attributes of the root element, which is all we need from `AndroidManifest.xml`.
 */
object BinaryXmlParser {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val UTF8_FLAG = 1 shl 8
    private const val NO_ENTRY = -1

    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val TYPE_FIRST_INT = 0x10
    private const val TYPE_LAST_INT = 0x1f

    /** Framework attribute ids, used when attribute names are stripped/obfuscated. */
    private val knownAttributes = mapOf(
        0x0101021b to "versionCode",
        0x0101021c to "versionName",
        0x01010577 to "isFeatureSplit",
    )

    fun readRootAttributes(data: ByteArray): Map<String, String> {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        require(data.size >= 8 && buf.u16(0) == RES_XML_TYPE) { "Not a binary XML file" }

        var strings: List<String> = emptyList()
        var resourceIds = IntArray(0)
        var pos = buf.u16(2)
        while (pos + 8 <= data.size) {
            val type = buf.u16(pos)
            val headerSize = buf.u16(pos + 2)
            val size = buf.getInt(pos + 4)
            require(size >= 8 && pos + size <= data.size) { "Corrupt chunk at $pos" }
            when (type) {
                RES_STRING_POOL_TYPE -> strings = readStringPool(buf, pos)
                RES_XML_RESOURCE_MAP_TYPE ->
                    resourceIds = IntArray((size - headerSize) / 4) { buf.getInt(pos + headerSize + it * 4) }
                RES_XML_START_ELEMENT_TYPE -> return readAttributes(buf, pos + headerSize, strings, resourceIds)
            }
            pos += size
        }
        throw IllegalArgumentException("No root element found")
    }

    private fun readAttributes(
        buf: ByteBuffer,
        ext: Int,
        strings: List<String>,
        resourceIds: IntArray,
    ): Map<String, String> {
        val attributeStart = buf.u16(ext + 8)
        val attributeSize = buf.u16(ext + 10)
        val attributeCount = buf.u16(ext + 12)
        val result = LinkedHashMap<String, String>()
        for (i in 0 until attributeCount) {
            val a = ext + attributeStart + i * attributeSize
            val nameIndex = buf.getInt(a + 4)
            val rawValue = buf.getInt(a + 8)
            val dataType = buf.get(a + 15).toInt() and 0xff
            val data = buf.getInt(a + 16)

            val name = resourceIds.getOrNull(nameIndex)?.let { knownAttributes[it] }
                ?: strings.getOrNull(nameIndex).orEmpty()
            if (name.isEmpty()) continue
            val value = when {
                rawValue != NO_ENTRY && rawValue in strings.indices -> strings[rawValue]
                dataType == TYPE_STRING -> strings.getOrNull(data) ?: continue
                dataType == TYPE_INT_BOOLEAN -> (data != 0).toString()
                dataType in TYPE_FIRST_INT..TYPE_LAST_INT -> data.toLong().and(0xffffffffL).toString()
                else -> continue
            }
            result[name] = value
        }
        return result
    }

    private fun readStringPool(buf: ByteBuffer, start: Int): List<String> {
        val headerSize = buf.u16(start + 2)
        val count = buf.getInt(start + 8)
        val flags = buf.getInt(start + 16)
        val stringsStart = buf.getInt(start + 20)
        val utf8 = flags and UTF8_FLAG != 0
        return List(count) { i ->
            val offset = buf.getInt(start + headerSize + i * 4)
            val p = start + stringsStart + offset
            if (utf8) readUtf8(buf, p) else readUtf16(buf, p)
        }
    }

    private fun readUtf8(buf: ByteBuffer, start: Int): String {
        var p = start
        // Skip UTF-16 length, then read UTF-8 byte length.
        p += if (buf.u8(p) and 0x80 != 0) 2 else 1
        var length = buf.u8(p)
        if (length and 0x80 != 0) {
            length = ((length and 0x7f) shl 8) or buf.u8(p + 1)
            p += 2
        } else {
            p += 1
        }
        val bytes = ByteArray(length)
        for (i in 0 until length) bytes[i] = buf.get(p + i)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16(buf: ByteBuffer, start: Int): String {
        var p = start
        var length = buf.u16(p)
        if (length and 0x8000 != 0) {
            length = ((length and 0x7fff) shl 16) or buf.u16(p + 2)
            p += 4
        } else {
            p += 2
        }
        val chars = CharArray(length) { buf.getChar(p + it * 2) }
        return String(chars)
    }

    private fun ByteBuffer.u16(index: Int): Int = getShort(index).toInt() and 0xffff
    private fun ByteBuffer.u8(index: Int): Int = get(index).toInt() and 0xff
}
