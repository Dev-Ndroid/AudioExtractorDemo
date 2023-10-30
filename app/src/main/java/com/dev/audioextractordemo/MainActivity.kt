package com.dev.audioextractordemo

import android.Manifest.permission.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import com.dev.audioextractordemo.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService
    var name = ""

    val videoPath = "/path/to/video.mp4"
    var outputAudioPath = ""
    lateinit var extractedAudioPath: String

    private var secondsRemaining = 0 // Initial time in seconds
    private var timerJob: Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                val getpermission = Intent()
                getpermission.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(getpermission)
            }
        }
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        startCamera()



        viewBinding.videoCaptureButton.setOnClickListener {
            captureVideo()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

   /* fun compressFf(filePath: String) {

        val rc = FFmpeg.execute("-i $filePath -c:v mpeg4 file2.mp4");

        if (rc == RETURN_CODE_SUCCESS) {
            Log.i(Config.TAG, "Command execution completed successfully.");
        } else if (rc == RETURN_CODE_CANCEL) {
            Log.i(Config.TAG, "Command execution cancelled by user.");
        } else {
            Log.i(Config.TAG, String.format("Command execution failed with rc=%d and the output below.", rc));
            Config.printLastCommandOutput(Log.INFO);
        }
    }*/


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Video
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.LOWEST,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Compression using LightCompressor Library
    private fun processVideo(uriList: List<Uri>) {
           /* val filePath = SiliCompressor.with(this).compressVideo(uriList.first(), Environment.DIRECTORY_MOVIES )
            Log.e("COMPRESSION", "Compressed FIle Path: $filePath")*/
        lifecycleScope.launch {
            VideoCompressor.start(
                context = applicationContext,
                uriList,
                isStreamable = false,
                sharedStorageConfiguration = SharedStorageConfiguration(
                    saveAt = SaveLocation.movies,
                    subFolderName = "Compressed-Videos"
                ),
                configureWith = Configuration(
                    videoNames = listOf(name),
                    quality = VideoQuality.MEDIUM,
                    isMinBitrateCheckEnabled = false,
//                    videoBitrateInMbps = 1,
                    disableAudio = false,
                    keepOriginalResolution = false
                ),
                listener = object : CompressionListener {
                    override fun onProgress(index: Int, percent: Float) {

                        Log.e("Compression", "Compression Percentage: $percent")
                    }

                    override fun onStart(index: Int) {
                        Log.e("Compression", "Compression Started")

                    }

                    override fun onSuccess(index: Int, size: Long, path: String?) {

//                        FileHelper.zip(path, "/storage/emulated/0/Movies/my-demo-videos", "ZippedVideo.zip", false)
                        val destinationDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

                        // Specify the name of the zipped file
                        val destinationFileName = "ZippedVideoFIle.7z"
                        Log.e("Compression", "Compression Success: $path || MOVIES PATH : $destinationDirectory")
                        // Call the zip method to create the zip file
                        if (FileHelper.zip(path, destinationDirectory.absolutePath+"/ZippedVideos", destinationFileName, false)) {
                            Log.d("Zip", "Zipped file saved in Movies folder")
                        } else {
                            Log.e("Zip", "Failed to zip and save the file")
                        }
                    }

                    override fun onFailure(index: Int, failureMessage: String) {
                        Log.e("Compression", "Compression Failed: $failureMessage")
                    }

                    override fun onCancelled(index: Int) {
                        Log.e("Compression", "compression has been cancelled")
                        // make UI changes, cleanup, etc
                    }
                },
            )
        }
    }

    private fun startTimer() {
        timerJob = CoroutineScope(Dispatchers.IO).launch {
            while (secondsRemaining <= 10) {
                // Update your UI elements with the remaining time
                // textView.text = secondsRemaining.toString()
                runOnUiThread {
                    viewBinding.timerTv.text = secondsRemaining.toString()
                }
                if (secondsRemaining == 10) {
                    runOnUiThread {
                        viewBinding.timerTv.text = secondsRemaining.toString()
                        viewBinding.videoCaptureButton.callOnClick()
                        viewBinding.timerTv.text = "0"
                        viewBinding.timerTv.visibility - View.GONE
                        secondsRemaining = 0
                    }
                    break
                }
                // Delay for 1 second
                delay(1000)
                secondsRemaining++
            }

            // Timer has finished, handle this event
            // For example, show a message or perform an action
        }
    }

    private fun captureVideo() {
        // Check if the VideoCapture use case has been created: if not, do nothing.
        val videoCapture = this.videoCapture ?: return

        viewBinding.videoCaptureButton.isEnabled = false

        // If there is an active recording in progress, stop it and release the current recording.
        // We will be notified when the captured video file is ready to be used by our application.
        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")

            // Create a subdirectory path within the cache directory
//            val subDirPath = "CameraX-Video"
//            val filePath = File(cacheDir, subDirPath)
//
//            // Create the subdirectory if it doesn't exist
//            filePath.mkdirs()
//
//            // Store the file in the subdirectory
//            put(MediaStore.MediaColumns.DATA, filePath.absolutePath)
        }


        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                withAudioEnabled()
            }
            .start(ContextCompat.getMainExecutor(this@MainActivity)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.videoCaptureButton.apply {
                            text = getString(com.dev.audioextractordemo.R.string.stop_capture)
                            isEnabled = true
                        }
                        viewBinding.timerTv.visibility = View.VISIBLE
                        startTimer()
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg =
                                "Video capture succeeded: ${recordEvent.outputResults.outputUri}"

                            Log.e(
                                "Video capture succeeded",
                                "RECORDED URI: ${recordEvent.outputResults.outputUri}"
                            )
                            val uri = recordEvent.outputResults.outputUri
                            val uriList = mutableListOf<Uri>()
                            uriList.add(uri)
                            processVideo(uriList)
                            val path = FileUtils.getPath(this, uri)
                            Log.e("Video capture succeeded", "RECORDED URI PATH: $path")

                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                            path?.let { videoPath ->
                                /*extractAudioFromVideo(videoPath) {

                                }
                                extractImageFrames(videoPath)*/
                            }


                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                        }
                        viewBinding.videoCaptureButton.apply {
                            text = getString(com.dev.audioextractordemo.R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                CAMERA,
                RECORD_AUDIO,
                READ_EXTERNAL_STORAGE
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
//                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }


}