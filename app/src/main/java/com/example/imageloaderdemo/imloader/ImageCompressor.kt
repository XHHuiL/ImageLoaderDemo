package com.example.imageloaderdemo.imloader

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileDescriptor

object ImageCompressor {

    // 从资源文件解码
    public fun decodeBitmapFromResource(res: Resources, resId: Int, reqWidth: Int, reqHeight: Int) : Bitmap? {
        val options = BitmapFactory.Options()
        // 只获取图片的宽高信息，不实际执行解析过程，解析后的信息通过options.outWidth、options.outHeight获取
        options.inJustDecodeBounds = true
        BitmapFactory.decodeResource(res, resId, options)

        // 计算采样率， >= 1 并且是2的整次幂
        options.inJustDecodeBounds = false
        options.inSampleSize =
            calculateInSampleSize(
                options,
                reqWidth,
                reqHeight
            )

        return BitmapFactory.decodeResource(res, resId, options)
    }

    // 从本地文件解码
    public fun decodeBitmapFromFileDescriptor(fd: FileDescriptor, reqWidth: Int, reqHeight: Int) : Bitmap? {
        val options = BitmapFactory.Options()
        // 只获取图片的宽高信息，不实际执行解析过程，解析后的信息通过options.outWidth、options.outHeight获取
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFileDescriptor(fd, null, options)

        // 计算采样率， >= 1 并且是2的整次幂
        options.inJustDecodeBounds = false
        options.inSampleSize =
            calculateInSampleSize(
                options,
                reqWidth,
                reqHeight
            )
        return BitmapFactory.decodeFileDescriptor(fd, null, options)
    }

    // 计算采样率，用于压缩图片
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int) : Int {
        var sampleSize = 1
        if (reqWidth == 0 || reqHeight == 0) {
            return sampleSize
        }
        val halfWidth = options.outWidth / 2
        val halfHeight = options.outHeight / 2
        while (halfWidth / sampleSize >= reqWidth && halfHeight / sampleSize >= reqHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

}