package com.bililisten.android.ui

import android.graphics.Bitmap
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode

/** Export frames produced by the real Compose screen as a small, shareable motion preview. */
internal class ForestAnimationPreview(file: File) : Closeable {
    private val writer = ImageIO.getImageWritersByFormatName("gif").next()
    private val output = ImageIO.createImageOutputStream(file.apply { parentFile!!.mkdirs() })
    private var first = true

    init { writer.output = output; writer.prepareWriteSequence(null) }

    fun frame(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)
        val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), writer.defaultWriteParam)
        val root = metadata.getAsTree(metadata.nativeMetadataFormatName) as IIOMetadataNode
        val control = root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode
        control.setAttribute("disposalMethod", "none")
        control.setAttribute("userInputFlag", "FALSE")
        control.setAttribute("transparentColorFlag", "FALSE")
        control.setAttribute("delayTime", "8")
        control.setAttribute("transparentColorIndex", "0")
        if (first) {
            val extensions = IIOMetadataNode("ApplicationExtensions")
            extensions.appendChild(IIOMetadataNode("ApplicationExtension").apply {
                setAttribute("applicationID", "NETSCAPE")
                setAttribute("authenticationCode", "2.0")
                userObject = byteArrayOf(1, 0, 0)
            })
            root.appendChild(extensions)
            first = false
        }
        metadata.setFromTree(metadata.nativeMetadataFormatName, root)
        writer.writeToSequence(IIOImage(image, null, metadata), writer.defaultWriteParam)
        image.flush()
    }

    override fun close() { writer.endWriteSequence(); output.close(); writer.dispose() }
}
