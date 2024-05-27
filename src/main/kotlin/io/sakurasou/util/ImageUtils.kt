package io.sakurasou.util

import io.sakurasou.entity.ImageSize
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

    suspend fun resize(image: BufferedImage, imageSize: ImageSize, quality: Double = 0.75): ByteArray {
        return withContext(Dispatchers.IO) {
            ByteArrayOutputStream().apply {
                val originalWidth = image.width
                val originalHeight = image.height
                val newHeight = (originalHeight * imageSize.width) / originalWidth
                Thumbnails.of(image)
                    .size(imageSize.width, newHeight)
                    .outputFormat(imageSize.type)
                    .outputQuality(quality)
                    .toOutputStream(this)
            }.toByteArray()
        }
    }

}