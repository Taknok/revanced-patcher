package app.revanced.patcher.util

import org.w3c.dom.Document
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class Document internal constructor(
    inputStream: InputStream,
) : Document by DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream), Closeable {
    private var file: File? = null

    init {
        normalize()
    }

    internal constructor(file: File) : this(file.inputStream()) {
        this.file = file
        readerCount.merge(file, 1, Int::plus)
    }

    override fun close() {
        file?.let {
            if (readerCount[it]!! > 1) {
                throw IllegalStateException(
                    "Two or more instances are currently reading $it." +
                        "To be able to close this instance, no other instances may be reading $it at the same time.",
                )
            } else {
                readerCount.remove(it)
            }

            val transformer = TransformerFactory.newInstance().newTransformer()
            if (isAndroid) {
                // Set to UTF-16 to prevent surrogate pairs from being escaped to broken numeric character references.
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16")
                // The XML declaration will have encoding="UTF-16", but we're going to write it back in UTF-8, so omit it.
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                // Use FileWriter to output the XML file in UTF-8 encoding.
                it.writer().use { writer ->
                    transformer.transform(DOMSource(this), StreamResult(writer))
                }
            } else {
                transformer.transform(DOMSource(this), StreamResult(it))
            }
        }
    }

    private companion object {
        private val readerCount = mutableMapOf<File, Int>()
        private val isAndroid = System.getProperty("java.runtime.name").equals("Android Runtime")
    }
}
