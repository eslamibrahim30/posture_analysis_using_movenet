package com.example.grad_project

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.grad_project.ml.Lightning
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

class CorrectorActivity : AppCompatActivity() {
    private val paint = Paint()
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var model: Lightning
    private lateinit var bitmap: Bitmap
    private lateinit var imageView: ImageView
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var badPostureStartTime: Long = 0
    private var isBadPosture: Boolean = false
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var executorService: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_corrector)
        get_permissions()

        imageProcessor = ImageProcessor.Builder().add(ResizeOp(192, 192, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = Lightning.newInstance(this)
        imageView = findViewById(R.id.correctorImageView)
        textureView = findViewById(R.id.correctorTitle)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        paint.color = Color.YELLOW

        // Initialize media player for warning sound
        mediaPlayer = MediaPlayer.create(this, R.raw.warning)
        executorService = Executors.newSingleThreadExecutor()

        // Show instructions dialog
        showInstructionsDialog()

        textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var tensorImage = TensorImage(DataType.UINT8)
                tensorImage.load(bitmap)
                tensorImage = imageProcessor.process(tensorImage)

                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 192, 192, 3), DataType.UINT8)
                inputFeature0.loadBuffer(tensorImage.buffer)

                val outputs = model.process(inputFeature0)
                val outputFeature0 = outputs.outputFeature0AsTensorBuffer.floatArray

                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                var canvas = Canvas(mutable)
                val h = bitmap.height
                val w = bitmap.width

                // Draw key points and check posture
                val keyPoints = mutableListOf<FloatArray>()
                for (i in outputFeature0.indices step 3) {
                    if (outputFeature0[i + 2] > 0.20) {
                        val x = outputFeature0[i + 1] * w
                        val y = outputFeature0[i] * h
                        canvas.drawCircle(x, y, 10f, paint)
                        keyPoints.add(floatArrayOf(x, y))
                    } else {
                        keyPoints.add(floatArrayOf(Float.NaN, Float.NaN))
                    }
                }

                // Draw skeleton
                drawSkeleton(canvas, keyPoints)

                // Check posture
                val posture = checkPosture(keyPoints)
                if (posture == "bad") {
                    if (!isBadPosture) {
                        isBadPosture = true
                        badPostureStartTime = System.currentTimeMillis()
                    } else {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - badPostureStartTime >= 3000) {
                            if (!mediaPlayer.isPlaying) {
                                executorService.submit { mediaPlayer.start() }
                            }
                        }
                    }
                } else {
                    isBadPosture = false
                }

                imageView.setImageBitmap(mutable)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        handlerThread.quitSafely()
        mediaPlayer.release()
        executorService.shutdown()
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                val captureRequest = p0.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val surface = Surface(textureView.surfaceTexture)
                captureRequest.addTarget(surface)
                p0.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {}
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {}

            override fun onError(p0: CameraDevice, p1: Int) {}
        }, handler)
    }

    fun get_permissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) get_permissions()
    }

    private fun showInstructionsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_instructions, null)

        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Got it!") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)

        val dialog = dialogBuilder.create()
        dialog.show()
    }

    private fun checkPosture(keyPoints: List<FloatArray>): String {
        // Example: Calculate the angle between shoulders and hip to determine upper body posture
        // Assuming keyPoints[5] is left shoulder, keyPoints[6] is right shoulder, keyPoints[11] is left hip, keyPoints[12] is right hip

        val leftShoulder = keyPoints[5]
        val rightShoulder = keyPoints[6]
        val leftHip = keyPoints[11]
        val rightHip = keyPoints[12]

        // Check if all required keypoints are detected
        if (leftShoulder.any { it.isNaN() } || rightShoulder.any { it.isNaN() }) {
            return "good" // Not all keypoints are detected
        }

        // Calculate the angle between shoulders and hip
        val angleLeft = if (!leftHip.any { it.isNaN() }) calculateAngle(leftShoulder, rightShoulder, leftHip) else Float.NaN
        val angleRight = if (!rightHip.any { it.isNaN() }) calculateAngle(leftShoulder, rightShoulder, rightHip) else Float.NaN

        // Determine posture based on angles (adjust thresholds as needed)
        return if ((!angleLeft.isNaN() && (angleLeft < 75 || angleLeft > 105)) ||
            (!angleRight.isNaN() && (angleRight < 75 || angleRight > 105))) {
            "bad"
        } else {
            "good"
        }
    }

    private fun calculateAngle(pointA: FloatArray, pointB: FloatArray, pointC: FloatArray): Float {
        val ba = floatArrayOf(pointA[0] - pointB[0], pointA[1] - pointB[1])
        val bc = floatArrayOf(pointC[0] - pointB[0], pointC[1] - pointB[1])
        val cosineAngle = (ba[0] * bc[0] + ba[1] * bc[1]) / (sqrt(ba[0].pow(2) + ba[1].pow(2)) * sqrt(bc[0].pow(2) + bc[1].pow(2)))
        return Math.toDegrees(acos(cosineAngle).toDouble()).toFloat()
    }

    private fun drawSkeleton(canvas: Canvas, keyPoints: List<FloatArray>) {
        val connections = listOf(
            Pair(5, 7), // Left shoulder to left elbow
            Pair(7, 9), // Left elbow to left wrist
            Pair(6, 8), // Right shoulder to right elbow
            Pair(8, 10), // Right elbow to right wrist
            Pair(5, 11), // Left shoulder to left hip
            Pair(6, 12), // Right shoulder to right hip
            Pair(11, 13), // Left hip to left knee
            Pair(13, 15), // Left knee to left ankle
            Pair(12, 14), // Right hip to right knee
            Pair(14, 16)  // Right knee to right ankle
        )

        for (connection in connections) {
            val start = keyPoints[connection.first]
            val end = keyPoints[connection.second]

            if (start.all { !it.isNaN() } && end.all { !it.isNaN() }) {
                canvas.drawLine(start[0], start[1], end[0], end[1], paint)
            }
        }
    }
}