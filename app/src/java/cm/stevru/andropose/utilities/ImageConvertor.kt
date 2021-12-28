// source: https://github.com/tensorflow/examples/blob/master/lite/examples/posenet/android/app/src/main/java/org/tensorflow/lite/examples/posenet/ImageUtils.kt

package cm.stevru.andropose.utilities

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs


object ImageConvertor {
    // This value is 2 ^ 18 - 1, and is used to hold the RGB values together before their ranges
    // are normalized to eight bits.
    private const val MAX_CHANNEL_VALUE = 262143

    val TAG : String ="ImageConvertor"

    /** Helper function to convert y,u,v integer values to RGB format */
    private fun convertYUVToRGB(y: Int, u: Int, v: Int): Int {
        // Adjust and check YUV values
        val yNew = if (y - 16 < 0) 0 else y - 16
        val uNew = u - 128
        val vNew = v - 128
        val expandY = 1192 * yNew
        var r = expandY + 1634 * vNew
        var g = expandY - 833 * vNew - 400 * uNew
        var b = expandY + 2066 * uNew

        // Clipping RGB values to be inside boundaries [ 0 , MAX_CHANNEL_VALUE ]
        val checkBoundaries = { x: Int ->
            when {
                x > MAX_CHANNEL_VALUE -> MAX_CHANNEL_VALUE
                x < 0 -> 0
                else -> x
            }
        }
        r = checkBoundaries(r)
        g = checkBoundaries(g)
        b = checkBoundaries(b)
        return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
    }

    /** Converts YUV420 format image data (ByteArray) into ARGB8888 format with IntArray as output. */
    fun convertYUV420ToARGB8888(
        yData: ByteArray,
        uData: ByteArray,
        vData: ByteArray,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        out: IntArray
    ) {
        var outputIndex = 0
        for (j in 0 until height) {
            val positionY = yRowStride * j
            val positionUV = uvRowStride * (j shr 1)

            for (i in 0 until width) {
                val uvOffset = positionUV + (i shr 1) * uvPixelStride

                // "0xff and" is used to cut off bits from following value that are higher than
                // the low 8 bits
                out[outputIndex] = convertYUVToRGB(
                    0xff and yData[positionY + i].toInt(), 0xff and uData[uvOffset].toInt(),
                    0xff and vData[uvOffset].toInt()
                )
                outputIndex += 1
            }
        }
    }

    fun resizeBitmap_old(image: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        var width: Int = image.getWidth()
        var height: Int = image.getHeight()

        Log.i(TAG,"Image initial size: $width--$height")

        if (width > height) {
            // landscape
            val ratio :Float = (width / maxWidth).toFloat()
            width = maxWidth
            height = (height / ratio).toInt()
        } else if (height > width) {
            // portrait
            val ratio = height.toFloat() / maxHeight
            height = maxHeight
            width = (width / ratio).toInt()
        } else {
            // square
            height = maxHeight
            width = maxWidth
        }

        (Log.i(TAG,"Image resized size: $width--$height"))

        val resizedImage = Bitmap.createScaledBitmap(image, width, height, true)
        return resizedImage
    }

    /** Crop Bitmap to maintain aspect ratio of model input.   */
    fun resizeBitmap(bitmap: Bitmap,width:Int,height:Int): Bitmap {
        val bitmapRatio = bitmap.height.toFloat() / bitmap.width
        val modelInputRatio = width.toFloat() / height
        var croppedBitmap = bitmap
        Log.i(TAG,"Image initial size: ${croppedBitmap.width}--${croppedBitmap.height}")

        // Acceptable difference between the modelInputRatio and bitmapRatio to skip cropping.
        val maxDifference = 1e-5

        // Checks if the bitmap has similar aspect ratio as the required model input.
        when {
            abs(modelInputRatio - bitmapRatio) < maxDifference -> croppedBitmap = bitmap //return croppedBitmap
            modelInputRatio < bitmapRatio -> {
                // New image is taller so we are height constrained.
                val cropHeight = bitmap.height - (bitmap.width.toFloat() / modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    (cropHeight / 2).toInt(),
                    bitmap.width,
                    (bitmap.height - cropHeight).toInt()
                )
            }
            else -> {
                val cropWidth = bitmap.width - (bitmap.height.toFloat() * modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    (cropWidth / 2).toInt(),
                    0,
                    (bitmap.width - cropWidth).toInt(),
                    bitmap.height
                )
            }
        }

        Log.i(TAG,"Image rezised for model size:${croppedBitmap.width},${croppedBitmap.height}")

        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, width, height, true)
        Log.i(ImageConvertor.TAG,"Image resized and scaled size: ${scaledBitmap.width}--${scaledBitmap.height}")
        return  scaledBitmap
        //return croppedBitmap


    }

    fun resizeAndCropImage(bitmap: Bitmap,width:Int,height: Int):Bitmap{
        val resizedBitmap = resizeBitmap(bitmap, width, height)
        Log.i(TAG,"Image rezised for model size:${resizedBitmap.width},${resizedBitmap.height}")

        val scaledBitmap = Bitmap.createScaledBitmap(resizedBitmap, width, height, true)
        Log.i(ImageConvertor.TAG,"Image resized and scaled size: ${scaledBitmap.width}--${scaledBitmap.height}")
        return  scaledBitmap
    }
}