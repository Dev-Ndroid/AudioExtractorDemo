package com.dev.audioextractordemo

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.arthenica.mobileffmpeg.FFmpeg
import com.dev.audioextractordemo.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    val videoPath = "/path/to/video.mp4"
    var outputAudioPath = ""
    lateinit var extractedAudioPath: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }


        viewBinding.audioPlayButton.setOnClickListener {
            playAudio()
        }

        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor = Executors.newSingleThreadExecutor()

    }


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
                        Quality.HIGHEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
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

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")

            // Create a subdirectory path within the cache directory
            val subDirPath = "CameraX-Video"
            val filePath = File(cacheDir, subDirPath)

            // Create the subdirectory if it doesn't exist
            filePath.mkdirs()

            // Store the file in the subdirectory
            put(MediaStore.MediaColumns.DATA, filePath.absolutePath)
        }


        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
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
                            val path = FileUtils.getPath(this, uri)
                            Log.e("Video capture succeeded", "RECORDED URI PATH: $path")

                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                            path?.let { videoPath ->
                                extractAudioFromVideo(videoPath) {

                                }
                                extractImageFrames(videoPath)
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


    private fun extractImageFrames(videoPath: String) {
        // Implement image frame extraction logic here
        // You can use FFmpeg or MediaMetadataRetriever to extract frames at specific times

        try {
            Log.e("URI", videoPath)
            val mediaMetadataRetriever = MediaMetadataRetriever()
            val file = File(videoPath)
            mediaMetadataRetriever.setDataSource(file.absolutePath)

            val timeInSeconds1 = 5
            val timeInSeconds2 = 10
// Original Vid
// Save images and Audio Files.
            val frame1 = mediaMetadataRetriever.getFrameAtTime(
                (timeInSeconds1 * 1000000).toLong(),
                MediaMetadataRetriever.OPTION_CLOSEST
            )
//            val frame2 = mediaMetadataRetriever.getFrameAtTime((timeInSeconds2 * 1000000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)

            // Use the extracted frames as needed
            // For example, display them in an ImageView
//        imageView1.setImageBitmap(frame1)
//        imageView2.setImageBitmap(frame2)

            val intent = Intent(this, PlaybackActivity::class.java)

            intent.putExtra("URI", videoPath)

            Helper.frame1 = frame1
            startActivity(intent)
            // Remember to release the MediaMetadataRetriever
//            mediaMetadataRetriever.release()
        } catch (ex: Exception) {
            // something went wrong with the file, ignore it and continue
            Log.e("MY_EXCEPTION", ex.toString())
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


    private fun extractAudioFromVideo(videoUri: String, onSucces: (String) -> Unit) {
        val outputDir = cacheDir
        val outputFile = File(outputDir, "extractedAudio.aac")
        extractedAudioPath = outputFile.absolutePath

        val command = "-i $videoUri -vn -c:a copy $extractedAudioPath"

        FFmpeg.executeAsync(command, object : ExecuteCallback {
            override fun apply(executionId: Long, returnCode: Int) {
                if (returnCode == Config.RETURN_CODE_SUCCESS) {
                    // Extraction successful
                    Log.e("AUDIO_EXTRACTION", "COMPLETE")
                    onSucces.invoke("XYZ")
                } else {
                    // Extraction failed
                    Log.e("AUDIO_EXTRACTION", "FAILED")
                }
            }
        })
    }

    private fun playAudio() {
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(extractedAudioPath)
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mediaPlayer.setOnCompletionListener { mp ->
            // Handle completion event
        }

        mediaPlayer.setOnErrorListener { mp, what, extra ->
            // Handle error event
            true // Return true to indicate that the error has been handled
        }
    }





}