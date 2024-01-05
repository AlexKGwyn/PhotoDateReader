import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.File
import java.util.LinkedHashMap

class ImageLoader(maxCacheSize: Int) {

    private val cache: LinkedHashMap<String, BufferedImage> =
        object : LinkedHashMap<String, BufferedImage>(maxCacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BufferedImage>): Boolean {
                return size > maxCacheSize
            }
        }

    suspend fun loadImage(filePath: String): BufferedImage {
        val cached = cache[filePath]
        if (cached != null) {
            return cached
        } else {
            val image = loadImageFromFile(filePath)
            cache[filePath] = image
            return image
        }
    }

    suspend fun loadImage(file: File): BufferedImage {
        return loadImage(file.path)
    }

    private suspend fun loadImageFromFile(filePath: String): BufferedImage =
        withContext(Dispatchers.IO) {
            return@withContext ImageIO.read(File(filePath))
        }
}
