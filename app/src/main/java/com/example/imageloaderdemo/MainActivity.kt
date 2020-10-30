package com.example.imageloaderdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.example.imageloaderdemo.imloader.ImageLoader
import com.example.imageloaderdemo.imloader.ImageLoaderConfig

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val config = ImageLoaderConfig()
        config.setCacheLevel(ImageLoaderConfig.CacheLevel.FULL)
        ImageLoader.initialize(this, config)
        val imageView = findViewById<ImageView>(R.id.iv)
        ImageLoader.loadBitmap("https://ss2.bdstatic.com/70cFvnSh_Q1YnxGkpoWK1HF6hhy/it/u=3724905587,3382434139&fm=26&gp=0.jpg", imageView, 0, 0)
    }
}