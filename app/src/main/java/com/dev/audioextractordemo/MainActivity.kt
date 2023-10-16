package com.dev.audioextractordemo

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.arthenica.mobileffmpeg.FFmpeg
import com.dev.audioextractordemo.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
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
    val aspectRatio = AspectRatio.RATIO_16_9
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null


//    private lateinit var viewFinder: PreviewView

    private lateinit var cameraExecutor: ExecutorService

    val videoPath = "/path/to/video.mp4"
    var outputAudioPath = ""
    var extractedAudioPath: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(aspectRatio).build()
        startCamera()
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }


       /* viewBinding.audioPlayButton.setOnClickListener {
            playAudio()
        }*/

//        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }

        viewBinding.imageCaptureButton.setOnClickListener {
            captureImage(this)
        }



    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Video
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.LOWEST,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                bindCameraUseCases(cameraProvider, preview, cameraSelector)

                // Bind use cases to camera
//                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    private fun captureImage(context: Context) {
        val imageCapture = this.imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
//                    val croppedParentBitmap = cropBitmapToRect(bitmap, viewBinding.viewFinder)
//                    val croppedBitmap = cropBitmapToRect(bitmap, viewBinding.rectangularBorder)
                    val croppedBitmap = cropImage(bitmap, viewBinding.viewFinder, viewBinding.rectangularBorder)

                    Helper.frame1 = croppedBitmap
                    val intent = Intent(context, PlaybackActivity::class.java)

                    intent.putExtra("URI", videoPath)

                    startActivity(intent)
                    // Save or use the croppedBitmap as needed
//                     saveBitmapToFile(croppedBitmap)

                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun cropBitmapToRect(bitmap: Bitmap, rectView: View): Bitmap {
        val left = rectView.left.coerceIn(0, bitmap.width)
        val top = rectView.top.coerceIn(0, bitmap.height)
        val right = rectView.right.coerceIn(0, bitmap.width)
        val bottom = rectView.bottom.coerceIn(0, bitmap.height)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            // Handle the case where the cropping dimensions are invalid
            // You can return the original bitmap or handle the error as needed
            return bitmap
        }

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }


    // Extension function to convert ImageProxy to Bitmap
    private fun ImageProxy.toBitmap(): Bitmap {
        val planeProxy = planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bufferBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Some devices may return images rotated. You can adjust the rotation if needed.
        val rotationDegrees = when (imageInfo.rotationDegrees) {
            90 -> 270
            270 -> 90
            else -> 0
        }
        val croppedBitmap = cropBitmapToRect(bufferBitmap, viewBinding.rectangularBorder)
//        val bitmapA = cropImage(bufferBitmap, viewBinding.viewFinder, viewBinding.rectangularBorder)
        return bufferBitmap
    }

    private fun cropImage(bitmap: Bitmap, frame: View, reference: View): Bitmap {
        val heightOriginal = frame.height
        val widthOriginal = frame.width
        val heightFrame = reference.height
        val widthFrame = reference.width
        val leftFrame = reference.left
        val topFrame = reference.top
        val heightReal = bitmap.height
        val widthReal = bitmap.width
        val widthFinal = widthFrame * widthReal / widthOriginal
        val heightFinal = heightFrame * heightReal / heightOriginal
        val leftFinal = leftFrame * widthReal / widthOriginal
        val topFinal = topFrame * heightReal / heightOriginal
        val bitmapFinal = Bitmap.createBitmap(
            bitmap,
            leftFinal, topFinal, widthFinal, heightFinal
        )
        /*val stream = ByteArrayOutputStream()
        bitmapFinal.compress(
            Bitmap.CompressFormat.JPEG,
            100,
            stream
        ) //100 is the best quality possibe
        return stream.toByteArray()*/
        return bitmapFinal
    }

    /*private fun cropBitmapToRect(bitmap: Bitmap, rectView: View): Bitmap {
        // Get the dimensions of the ViewFinder (PreviewView)
        val viewFinderWidth = viewBinding.viewFinder.width
        val viewFinderHeight = viewBinding.viewFinder.height

        // Get the dimensions and position of the rectangularBorder
        val left = rectView.left
        val top = rectView.top
        val right = rectView.right
        val bottom = rectView.bottom

        // Calculate the cropping coordinates relative to the ViewFinder
        val leftInPreview = left * bitmap.width / viewFinderWidth
        val topInPreview = top * bitmap.height / viewFinderHeight
        val rightInPreview = right * bitmap.width / viewFinderWidth
        val bottomInPreview = bottom * bitmap.height / viewFinderHeight

        // Ensure that the coordinates are within valid bounds
        val leftCropped = leftInPreview.coerceIn(0, bitmap.width)
        val topCropped = topInPreview.coerceIn(0, bitmap.height)
        val rightCropped = rightInPreview.coerceIn(0, bitmap.width)
        val bottomCropped = bottomInPreview.coerceIn(0, bitmap.height)

        // Calculate the width and height of the cropped region
        val width = rightCropped - leftCropped
        val height = bottomCropped - topCropped

        // Create the cropped bitmap
        if (width > 0 && height > 0) {
            return Bitmap.createBitmap(bitmap, leftCropped, topCropped, width, height)
        } else {
            // Handle the case where the cropping dimensions are invalid
            // You can return the original bitmap or handle the error as needed
            return bitmap
        }
    }*/


    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "IMG_$timeStamp.jpg"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, fileName)
    }

    private fun bindCameraUseCases(
        cameraProvider: ProcessCameraProvider,
        preview: Preview,
        cameraSelector: CameraSelector
    ) {

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(aspectRatio)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(this)) { image ->
                    // You can analyze each camera frame here
                }
            }

        // Patch Code
        val viewPort: ViewPort = ViewPort.Builder(
            Rational(
                viewBinding.viewFinder.width,
                viewBinding.viewFinder.height
            ),
            viewBinding.viewFinder.display.rotation
        ).setScaleType(ViewPort.FILL_CENTER).build()

        val useCaseGroupBuilder: UseCaseGroup.Builder = UseCaseGroup.Builder().setViewPort(
            viewPort
        )

        useCaseGroupBuilder.addUseCase(preview)
        useCaseGroupBuilder.addUseCase(imageCapture!!)
        useCaseGroupBuilder.addUseCase(imageAnalysis)



        //End Patch Code

        try {
            // Unbind any previous use cases
            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this, cameraSelector,
                useCaseGroupBuilder.build()
            )
//            // Bind new use cases
//            cameraProvider.bindToLifecycle(
//                this, cameraSelector, preview, imageCapture, imageAnalysis
//            )
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
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

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    /*private fun captureVideo() {
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

    }*/

    /*private fun extractAudioFromVideo(videoUri: String, onSucces: (String) -> Unit) {
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
    }*/


    /*private fun captureVideo() {
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
                            text = getString(R.string.stop_capture)
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
                                val compressedVideoPath = compressVideo(videoPath)
                                Log.e("Compressed Video Path:", compressedVideoPath)
                            }
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                        }
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }*/

    /*private fun compressVideo(videoPath: String): String {
        val outputDir = cacheDir
        val outputFile = File(outputDir, "compressedVideo.mp4")
        val compressedVideoPath = outputFile.absolutePath

        val command =
            "-i $videoPath -vf scale=720:-1 -c:v libx264 -crf 28 -preset fast -c:a aac -b:a 128k $compressedVideoPath"

        FFmpeg.execute(command)

        return compressedVideoPath
    }*/


}