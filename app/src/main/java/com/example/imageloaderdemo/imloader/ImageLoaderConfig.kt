package com.example.imageloaderdemo.imloader

public class ImageLoaderConfig {

    // 缓存级别，如指定为DISK，则只会从内存、磁盘中加载图片，图片不存在也不会通过网络下载图片
    public enum class CacheLevel {
        FULL,
        MEMORY,
        DISK
    }

    private var diskCacheDirName = "bitmap"

    public fun getDiskCacheDirName() : String {
        return diskCacheDirName
    }

    public fun setDiskCacheDirname(dirName: String) {
        this.diskCacheDirName = dirName
    }

    // 默认磁盘缓存的大小为50MB
    private var diskCacheSize = 1024 * 1024 * 50L

    public fun getDiskCacheSize() : Long {
        return diskCacheSize
    }

    public fun setDiskCacheSize(size: Long) {
        this.diskCacheSize = size
    }

    private var cacheLevel: CacheLevel = CacheLevel.FULL

    public fun getCacheLevel() : CacheLevel {
        return cacheLevel
    }

    public fun setCacheLevel(cacheLevel: CacheLevel) {
        this.cacheLevel = cacheLevel
    }

}