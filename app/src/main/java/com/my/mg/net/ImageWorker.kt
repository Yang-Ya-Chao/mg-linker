package com.my.mg.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.my.mg.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object ImageWorker {
    /**
     * 挂起函数：加载图片
     * 包含 Bitmap 采样，防止 OOM (Out Of Memory)
     */
    suspend fun loadCarImageSuspended(
        context: Context, views: RemoteViews, carImageUrl: String
    ) = withContext(Dispatchers.IO) {
        if (carImageUrl.isEmpty()) {
            views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
            return@withContext
        }

        try {
            val cacheDir = File(context.filesDir, "car_image_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            // 使用 hash 保证文件名合法
            val fileName = carImageUrl.hashCode().toString() + ".img"
            val imageFile = File(cacheDir, fileName)

            // 缓存策略：如果文件不存在，才下载
            if (!imageFile.exists()) {
                // 清理旧缓存 (删除该目录下所有非当前图片的文件)
                cacheDir.listFiles()?.forEach { if (it.name != fileName) it.delete() }
                downloadAndSaveImage(carImageUrl, imageFile)
            }

            if (imageFile.exists()) {
                // 关键修复：使用采样解码，限制图片大小
                // 300x200 足够小组件显示，避免加载几兆的原图
                val bitmap = decodeSampledBitmap(imageFile.absolutePath, 300, 200)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.iv_car_image, bitmap)
                } else {
                    // 解码失败回退
                    views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
        }
    }

    private fun downloadAndSaveImage(urlStr: String, outFile: File) {
        val url = URL(urlStr)
        val connection = url.openConnection()
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.connect()
        connection.getInputStream().use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    // 防止 OOM 的关键方法：计算采样率
    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true // 只读取尺寸，不加载内存
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false // 真正加载

        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

}