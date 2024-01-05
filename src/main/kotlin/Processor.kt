import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.opencv.global.opencv_core.*
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.global.tesseract
import org.bytedeco.tesseract.global.tesseract.*
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import kotlin.concurrent.thread

object Processor {

    const val H_MIN = 15.0
    const val H_MAX = 35.0
    const val S_MIN = 40.0
    const val S_MAX = 255.0
    const val V_MIN = 170.0
    const val V_MAX = 255.0

    fun thresholdImage(image: BufferedImage): Mat {
        val mat = image.toMat()
        val hsvMat = Mat()
        cvtColor(mat, hsvMat, CV_BGR2HSV_FULL)
        val lowerThreshold = Mat(1, 1, CV_32SC4, Scalar(H_MIN, S_MIN, V_MIN, 0.0)) //Mat(0.0, 0.0, 128.0, 0.0) // Adjust these values
        val upperThreshold = Mat(1, 1, CV_32SC4, Scalar(H_MAX, S_MAX, V_MAX, 0.0))
        val thresholdMat = Mat()

        inRange(hsvMat, lowerThreshold, upperThreshold, thresholdMat)
        //GaussianBlur(thresholdMat, thresholdMat, Size(11, 11), 0.0)

        val erodedMat = Mat()
        val kernel = getStructuringElement(MORPH_RECT, Size(3, 3))
        morphologyEx(thresholdMat, erodedMat, MORPH_OPEN, kernel)
        val dilateKernel = getStructuringElement(MORPH_RECT, Size(5, 3))
        val dilateKernelVertical = getStructuringElement(MORPH_RECT, Size(1, 1))
        dilate(erodedMat, thresholdMat, dilateKernel)
        //dilate(, erodedMat, dilateKernelVertical)


        val binaryMat = Mat()
        threshold(thresholdMat, binaryMat, 128.0, 255.0, THRESH_BINARY or THRESH_OTSU)
        bitwise_not(binaryMat, thresholdMat)
        return thresholdMat
    }

    private fun rotate(image: Mat): Mat {
        val rotatedImage = Mat()
        transpose(image, rotatedImage)
        flip(rotatedImage, rotatedImage, 1) // 1 denotes a horizontal flip

        return rotatedImage
    }

    private fun Mat.cropRelativeArea(horizontalStart:Float, horizontalEnd:Float, verticalStart:Float, verticalEnd:Float):Mat{
        val width = this.cols()
        val height = this.rows()

        // Calculate absolute coordinates
        val x = (horizontalStart * width).toInt()
        val y = (verticalStart * height).toInt()
        val w = ((horizontalEnd - horizontalStart) * width).toInt()
        val h = ((verticalEnd - verticalStart) * height).toInt()

        // Create a rectangle for cropping
        val rect = org.bytedeco.opencv.opencv_core.Rect(x, y, w, h)

        // Crop and return the new image
        return Mat(this, rect)
    }

    suspend fun processImage(image: BufferedImage):BufferedImage = withContext(Dispatchers.Default) {
        var thresholdMat = thresholdImage(image)

        if(thresholdMat.rows() > thresholdMat.cols() ){
            thresholdMat = rotate(thresholdMat)
        }

        thresholdMat = thresholdMat.cropRelativeArea(.75f, 1f, .8f, 1f)



        val tessBaseAPI = TessBaseAPI()
        if (tessBaseAPI.Init("trainingData/", "7seg") != 0) {
            throw RuntimeException("Could not initialize tesseract.")
        }

        tessBaseAPI.SetVariable("tessedit_char_whitelist", "0123456789'")
        tessBaseAPI.SetVariable("classify_nonlinear_norm", "1")
        tessBaseAPI.SetPageSegMode(PSM_SINGLE_LINE)
        tessBaseAPI.SetVariable("load_system_dawg", "false");
        tessBaseAPI.SetVariable("load_freq_dawg", "false");

        tessBaseAPI.SetImage(
            thresholdMat.data().asByteBuffer(),
            thresholdMat.cols(),
            thresholdMat.rows(),
            thresholdMat.elemSize().toInt(),
            thresholdMat.step1().toInt()
        )

        println(tessBaseAPI.GetUTF8Text().string)
        val ri = tessBaseAPI.GetIterator()
        val level = tesseract.RIL_SYMBOL

        val datesWithCoordinates = mutableListOf<Pair<String, Rect>>()
        ri.use {
            while (ri != null) {
                // Get the text for the current symbol
                val symbolText = ri.GetUTF8Text(level)?.string ?:""
                // Get the bounding box for the current symbol
                val left = IntArray(1)
                val right = IntArray(1)
                val top = IntArray(1)
                val bottom = IntArray(1)
                ri.BoundingBox(level, left, top, right, bottom)
                datesWithCoordinates.add(
                    Pair(
                        symbolText,
                        Rect(
                            left.component1().toFloat(),
                            top.component1().toFloat(),
                            right.component1().toFloat(),
                            bottom.component1().toFloat()
                        )
                    )
                )
                if (!ri.Next(level)) {
                    break
                }
            }
        }

        println(datesWithCoordinates.joinToString())

        val ret = thresholdMat.toHSVImage()
        val graphics = ret.createGraphics()
        graphics.color = Color.RED
        graphics.stroke = BasicStroke(1f)
        datesWithCoordinates.forEach {
            val rect = it.second
            graphics.drawRect(
                rect.left.toInt(), rect.top.toInt(), rect.width.toInt(),
                rect.height.toInt()
            )
            graphics.drawString(it.first, rect.left, rect.top-3)
        }
        graphics.dispose()
        return@withContext ret

//        val dateFormat = SimpleDateFormat("yy MM dd")
//        val imageCenter = Offset((image.width / 2).toFloat(), (image.height / 2).toFloat())
//
//        datesWithCoordinates.mapNotNull { (dateString, rect) ->
//            try {
//                val date = dateFormat.parse(dateString)
//                val rectCenter = rect.center
//                val distance = sqrt(
//                    (rectCenter.x - imageCenter.x).toDouble()
//                        .pow(2.0) + (rectCenter.y - imageCenter.y).toDouble().pow(2.0)
//                )
//                Pair(date, distance)
//            } catch (e: Exception) {
//                null
//            }
//        }.maxByOrNull { it.second }?.first
    }


    fun BufferedImage.toMat(): Mat {
        val convertAlpha = this.type == BufferedImage.TYPE_4BYTE_ABGR || this.type == BufferedImage.TYPE_INT_ARGB

        val mat = if(convertAlpha) Mat(height, width, CV_8UC4) else Mat(height, width, CV_8UC3)
        val data = (raster.dataBuffer as DataBufferByte).data
        mat.data().put(data, 0, data.size)
        if(convertAlpha) {
            val bgrMat = Mat(height, width, CV_8UC3)
            cvtColor(mat, bgrMat, CV_BGRA2BGR)
            return bgrMat
        }
        else{
            return mat
        }
    }

    fun Mat.toHSVImage(): BufferedImage {
        val bgr = Mat()
        cvtColor(this, bgr, COLOR_GRAY2BGR)

        val cols = bgr.cols()
        val rows = bgr.rows()
        val elemSize = bgr.elemSize().toInt()

        val data = ByteArray(rows * cols * elemSize)
        bgr.data().get(data)

        val type =
            if (bgr.channels() > 1) BufferedImage.TYPE_3BYTE_BGR else BufferedImage.TYPE_BYTE_GRAY
        val image = BufferedImage(cols, rows, type)

        val targetPixels = (image.raster.dataBuffer as DataBufferByte).data
        System.arraycopy(data, 0, targetPixels, 0, data.size)

        return image
    }

    fun Mat.getBytes3Grouped(amount: Int = 10, startOffset: Long = 0L): String {
        val byteArray = ByteArray(amount * 3)
        val offset = this.data().getPointer(startOffset)
        offset.get(byteArray)
        return byteArray.toList().chunked(3).joinToString { (b1, b2, b3) -> " [${b1.toUByte()}, ${b2.toUByte()}, ${b3.toUByte()}]" }
    }
}
