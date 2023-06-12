package com.dev.audioextractordemo

import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import com.dev.audioextractordemo.databinding.ActivityMainBinding
import com.dev.audioextractordemo.databinding.ActivityPlaybackBinding
import java.io.File
import java.net.URI

class PlaybackActivity : AppCompatActivity() {
    lateinit var binding: ActivityPlaybackBinding
    var path = ""
    var frame1: Bitmap? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getStringExtra("URI")?.let {
            path = it
        }

        binding.imageIv.setImageBitmap(Helper.frame1)

         // Replace this with the actual method to retrieve the recorded video URI
        Log.e("PATH", "STRING: $path || PATH: ${path.toUri()}")
        binding.videoView.setVideoURI(Uri.fromFile(File(path)))
        binding.videoView.start()

    }
}