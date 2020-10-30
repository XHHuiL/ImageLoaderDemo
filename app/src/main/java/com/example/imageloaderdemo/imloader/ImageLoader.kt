package com.example.imageloaderdemo.imloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.LruCache
import android.widget.ImageView
import com.jakewharton.disklrucache.DiskLruCache
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import com.example.imageloaderdemo.imloader.ImageLoaderConfig.CacheLevel

object ImageLoader {
    private var diskCacheDirName: String = "bitmap"
    private var diskCacheSize: Long = 1024 * 1024 * 50L
    private var cacheLevel: CacheLevel = CacheLevel.FULL

    private lateinit var mContext: Context
    private lateinit var mMemoryCache: LruCache<String, Bitmap>
    private lateinit var mDiskCache: DiskLruCache
    private var mDiskCacheCreated: Boolean = false

    private val mThreadPool = ThreadPoolExecutor(4, 8, 10,
        TimeUnit.SECONDS, LinkedBlockingDeque<Runnable>())

    private val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val result: Result = msg.obj as Result
            val imageView: ImageView = result.imageView
            imageView.setImageBitmap(result.bitmap)
        }
    }

    private class Result(var imageView: ImageView, var url: String, var bitmap: Bitmap)

    private fun config(imageLoaderConfig: ImageLoaderConfig) {
        diskCacheDirName = imageLoaderConfig.getDiskCacheDirName()
        diskCacheSize = imageLoaderConfig.getDiskCacheSize()
        cacheLevel = imageLoaderConfig.getCacheLevel()
    }

    public fun initialize(context: Context) {
        initialize(context, null)
    }

    public fun initialize(context: Context, imageLoaderConfig: ImageLoaderConfig?) {
        mContext = context.applicationContext
        imageLoaderConfig?.let {
            config(it)
        }
        // 获取当前可用的内存大小，单位KB
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        // 为ImageLoader的内存缓存分配当前可用内存的1/8，单位KB
        val cacheSize = (maxMemory / 8).toInt()
        mMemoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }

        // 设置磁盘缓存目录
        val diskCacheDir = getDiskCacheDir(mContext, diskCacheDirName)
        if (!diskCacheDir.exists()) {
            // 使用File.mkdirs()而不是File.mkdir()，保证路径上所有的文件夹均存在，不存在就创建
            diskCacheDir.mkdirs()
        }

        // 磁盘空间足够时，初始化磁盘缓存
        if (diskCacheDir.usableSpace > diskCacheSize) {
            try {
                mDiskCache = DiskLruCache.open(diskCacheDir, 1, 1, diskCacheSize)
                mDiskCacheCreated = true
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun getDiskCacheDir(context: Context, dirName: String) : File {
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED && !Environment.isExternalStorageRemovable()) {
            context.externalCacheDir?.let {
                return File(it.path + File.separator + dirName)
            }
        }
        return File(context.cacheDir.path + File.separator + dirName)
    }

    public fun loadBitmap(url: String, imageView: ImageView, reqWidth: Int, reqHeight: Int) {
        val bitmap = loadBitmapFromMemory(url)
        bitmap?.let {
            imageView.setImageBitmap(it)
            return
        }

        // 如果内存缓存未命中，异步加载
        mThreadPool.execute {
            loadBitmap(url, reqWidth, reqHeight)?.let {
                mHandler.obtainMessage(1, Result(imageView, url, it)).sendToTarget()
            }
        }
    }

    // 加载bitmap：内存 -> 磁盘 -> 网络
    private fun loadBitmap(url: String, reqWidth: Int, reqHeight: Int) : Bitmap? {
        var bitmap = loadBitmapFromMemory(url)
        bitmap?.let {
            return it
        }
        if (cacheLevel == CacheLevel.MEMORY) {
            return null
        }

        if (bitmap == null && !mDiskCacheCreated && cacheLevel == CacheLevel.FULL) {
            return loadBitmapFromHttp(url, reqWidth, reqHeight)
        }

        try {
            bitmap = loadBitmapFromDisk(url, reqWidth, reqHeight)
            bitmap?.let {
                return it
            }
            if (cacheLevel == CacheLevel.DISK) {
                return null
            }

            bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bitmap
    }

    private fun bytesToHexString(bytes: ByteArray) : String {
        val builder = StringBuilder()
        for (byte in bytes) {
            val hexString = Integer.toHexString(0xFF and byte.toInt())
            if (hexString.length == 1) {
                builder.append("0")
            }
            builder.append(hexString)
        }
        return builder.toString()
    }

    // 采用MD5编码，避免非法字符
    private fun hashKeyFromUrl(url: String) : String{
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(url.toByteArray())
            bytesToHexString(digest.digest())
        } catch (e: NoSuchAlgorithmException) {
            url.hashCode().toString()
        }
    }

    // 从内存加载bitmap
    private fun loadBitmapFromMemory(url: String) : Bitmap? {
        return mMemoryCache[hashKeyFromUrl(url)]
    }

    // 从磁盘加载bitmap
    private fun loadBitmapFromDisk(url: String, reqWidth: Int, reqHeight: Int) : Bitmap? {
        if (!this::mDiskCache.isInitialized) {
            return null
        }

        // 先获取snapshot对象，然后通过该对象获取文件输入流，这样就能获取到bitmap
        var bitmap: Bitmap? = null
        val key = hashKeyFromUrl(url)
        val snapshot = mDiskCache.get(key)
        snapshot?.let { snap ->
            bitmap = ImageCompressor.decodeBitmapFromFileDescriptor((snap.getInputStream(0) as FileInputStream).fd, reqWidth, reqHeight)
            bitmap?.let {
                mMemoryCache.put(key, it)
                return it
            }
        }
        return bitmap
    }

    // 从网络加载bitmap
    private fun loadBitmapFromHttp(url: String, reqWidth: Int, reqHeight: Int) : Bitmap? {
        if (!this::mDiskCache.isInitialized) {
            return downloadBitmap(url)
        }

        mDiskCache.edit(hashKeyFromUrl(url))?.apply {
            if (downLoadBitmapToStream(url, newOutputStream(0))) {
                commit()
            } else {
                abort()
            }
            mDiskCache.flush()
        }
        return loadBitmapFromDisk(url, reqWidth, reqHeight)
    }

    // 从网络下载图片至OutputStream以写入磁盘
    private fun downLoadBitmapToStream(url: String, outputStream: OutputStream) : Boolean {
        var connection: HttpURLConnection? = null
        var input: BufferedInputStream? = null
        var output: BufferedOutputStream? = null

        try {
            connection = URL(url).openConnection() as HttpURLConnection
            input = BufferedInputStream(connection.inputStream, 8 * 1024)
            output = BufferedOutputStream(outputStream, 8 * 1024)
            var b = input.read()
            while (b != -1) {
                output.write(b)
                b = input.read()
            }
            return true
        }catch (e: IOException) {

        } finally {
            connection?.disconnect()
            try {
                input?.close()
                output?.close()
            } catch (e: IOException) {

            }
        }
        return false
    }

    // 通过url，直接从网络下载bitmap，不经过缓存
    private fun downloadBitmap(url: String) : Bitmap? {
        var bitmap: Bitmap? = null
        var connection: HttpURLConnection? = null
        var input: BufferedInputStream? = null

        try {
            connection = URL(url).openConnection() as HttpURLConnection
            input = BufferedInputStream(connection.inputStream, 8 * 1024)
            bitmap = BitmapFactory.decodeStream(input)
        } catch (e: IOException) {

        } finally {
            connection?.disconnect()
            try {
                input?.close()
            } catch (e: IOException) {

            }
        }

        return bitmap
    }
}