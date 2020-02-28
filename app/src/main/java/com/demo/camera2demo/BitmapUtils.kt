package com.demo.camera2demo

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import okio.buffer
import okio.sink
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import android.os.Environment
import java.io.File

/**
 * @author daifalin
 * @date 2020-02-28 12:58
 * @ClassName FileUtil
 * @Description
 */
object BitmapUtils {
    /**
     * 图片转化为Byte[]
     */
    private fun toByteArray(bitmap: Bitmap): ByteArray {
        var os = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
        return os.toByteArray()
    }

    private fun mirror(rawBitmap: Bitmap): Bitmap {
        var matrix = Matrix()
        matrix.postScale(-1f, 1f)
        return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    }

    fun rotate(rawBitmap: Bitmap, degree: Float): Bitmap {
        var matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    }

    fun decodeBitmap(bitmap: Bitmap, reqWidth: Int, reqHeight: Int): Bitmap {
        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        var bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        BitmapFactory.decodeByteArray(bos.toByteArray(), 0, bos.size(), options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bos.toByteArray(), 0, bos.size(), options)
    }

    fun decodeBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap {
        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    fun decodeBitmapFromResource(res: Resources, resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeResource(res, resId, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(res, resId, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        var inSampleSize = 1

        if (rawWidth > reqWidth || rawHeight > reqHeight) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2

            while ((halfWidth / inSampleSize) > reqWidth && (halfHeight / inSampleSize) > reqHeight) {
                //设置inSampleSize为2的幂是因为解码器最终还是会对非2的幂的数进行向下处理，获取到最靠近2的幂的数
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun toBitmap(
        data: ByteArray?,
        onSuccess: (savedPath: Bitmap) -> Unit,
        onFailed: (msg: String) -> Unit
    ) {
        thread {
            try {
                val picFile = FileUtil.createCameraFile("camera2")
                if (picFile != null && data != null) {
                    val rawBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    val resultBitmap = mirror(rawBitmap)
                    onSuccess(resultBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onFailed("${e.message}")
            }
        }
    }

    fun savePic(
        data: ByteArray?,
        isMirror: Boolean = false,
        onSuccess: (savedPath: String, time: String) -> Unit,
        onFailed: (msg: String) -> Unit
    ) {
        thread {
            try {
                val temp = System.currentTimeMillis()
                val picFile = FileUtil.createCameraFile("camera2")
                if (picFile != null && data != null) {

                    val rawBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    val resultBitmap = if (isMirror) mirror(rawBitmap) else rawBitmap
                    picFile.sink().buffer().write(toByteArray(resultBitmap)).close()
                    onSuccess("${picFile.absolutePath}", "${System.currentTimeMillis() - temp}")

                    //log("图片已保存! 耗时：${System.currentTimeMillis() - temp}    路径：  ${picFile.absolutePath}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onFailed("${e.message}")
            }
        }
    }
}

/**
 * @author daifalin
 * @date 2020-02-28 12:58
 * @ClassName FileUtil
 * @Description
 */
object FileUtil {
    private val rootFolderPath = Environment.getExternalStorageDirectory().absolutePath + File.separator + "CameraDemo"

    fun createImageFile(isCrop: Boolean = false): File? {
        return try {
            var rootFile = File(rootFolderPath + File.separator + "capture")
            if (!rootFile.exists())
                rootFile.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = if (isCrop) "IMG_${timeStamp}_CROP.jpg" else "IMG_$timeStamp.jpg"
            File(rootFile.absolutePath + File.separator + fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createCameraFile(folderName: String = "camera2"): File? {
        return try {
            val rootFile = File(rootFolderPath + File.separator + "$folderName")
            if (!rootFile.exists())
                rootFile.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = "IMG_$timeStamp.jpg"
            File(rootFile.absolutePath + File.separator + fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createVideoFile(): File? {
        return try {
            var rootFile = File(rootFolderPath + File.separator + "video")
            if (!rootFile.exists())
                rootFile.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = "VIDEO_$timeStamp.mp4"
            File(rootFile.absolutePath + File.separator + fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
