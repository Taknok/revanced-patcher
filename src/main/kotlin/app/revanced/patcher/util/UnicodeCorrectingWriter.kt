package app.revanced.patcher.util

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer

internal class UnicodeCorrectingWriter(
    outputStream: OutputStream,
    charset: String = "UTF-8"
) : Writer() {

    private val writer = OutputStreamWriter(outputStream, charset)
    private var pendingHighSurrogate: Char? = null
    private var isEntity = false
    private var skipMetaEnd = false

    // check if we are writing an entity, and if yes suppose that what comes next is a surrogate
    // else write entity as is.
    // https://android.googlesource.com/platform/external/apache-xml/+/650a6cfd4d6b2d38b88ada03694ae19cc448d07b/src/main/java/org/apache/xml/serializer/ToStream.java#1609
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        // Check for '&#' at the start of a potential entity
        if (!isEntity && len == 2 && cbuf[off] == '&' && cbuf[off + 1] == '#') {
            isEntity = true
            return // do not write '&#' yet
        }

        // Check for ';' ending the entity
        if (isEntity && len == 1 && cbuf[off] == ';') {
            isEntity = false
            if (pendingHighSurrogate != null || skipMetaEnd) {
                skipMetaEnd = false
                return // skip writing the final ';'
            }
        }

        // If not in entity and no surrogate to manage, write directly
        if (!isEntity && pendingHighSurrogate == null) {
            writer.write(cbuf, off, len)
            return
        }

        // ch needs to be recalculated as ToStream.java#L1611 pass writer.write(Integer.toString(ch))
        val ch = cbuf.slice(off until (off + len)).joinToString("").toInt().toChar()

        when {
            Character.isHighSurrogate(ch) -> {
                pendingHighSurrogate = ch
            }

            Character.isLowSurrogate(ch) && pendingHighSurrogate != null -> {
                val codePoint = Character.toCodePoint(pendingHighSurrogate!!, ch)
                writer.write(Character.toChars(codePoint))
                pendingHighSurrogate = null
                skipMetaEnd = true // skip the last ';' requested by ToStream.java#l1612
            }

            else -> {
                pendingHighSurrogate?.let {
                    writer.write("&#")
                    writer.write(it.code)
                    writer.write(";")
                    pendingHighSurrogate = null
                }
                writer.write(ch.code)
            }
        }
    }

    override fun flush() {
        pendingHighSurrogate?.let {
            writer.write(it.code)
            pendingHighSurrogate = null
        }
        writer.flush()
    }

    override fun close() {
        flush()
        writer.close()
    }
}
