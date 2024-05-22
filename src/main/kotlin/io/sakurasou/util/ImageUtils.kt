package io.sakurasou.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.coobird.thumbnailator.Thumbnails
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

/**
 * @author mashirot
 * 2024/5/21 22:26
 */
object ImageUtils {

    suspend fun resize(image: BufferedImage, newWidth: Int, quality: Double = 0.75, type: String = "webp"): ByteArray {
        return withContext(Dispatchers.IO) {
            ByteArrayOutputStream().apply {
                val originalWidth = image.width
                val originalHeight = image.height
                val newHeight = (originalHeight * newWidth) / originalWidth
                Thumbnails.of(image)
                    .size(newWidth, newHeight)
                    .outputFormat(type)
                    .outputQuality(quality)
                    .toOutputStream(this)
            }.toByteArray()
        }
    }

}