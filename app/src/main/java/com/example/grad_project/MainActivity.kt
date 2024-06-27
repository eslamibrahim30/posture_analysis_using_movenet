package com.example.grad_project

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
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
import kotlin.math.atan2

class MainActivity : AppCompatActivity() {
    private val paint = Paint()
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var model: Lightning
    private lateinit var bitmap: Bitmap
    private lateinit var imageView: ImageView
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var executorService: ExecutorService
    private var badPostureCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getPermissions()

        imageProcessor = ImageProcessor.Builder().add(ResizeOp(192, 192, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = Lightning.newInstance(this)
        imageView = findViewById(R.id.correctorImageView)
        textureView = findViewById(R.id.correctorTitle)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        paint.apply {
            color = Color.YELLOW
            textSize = 20f // Set text size to make the font bigger
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) // Set text style to bold
        }

        // Initialize media player for warning sound
        mediaPlayer = MediaPlayer.create(this, R.raw.warning)
        executorService = Executors.newSingleThreadExecutor()

        // Show instructions dialog
        showInstructionsDialog()

        textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()
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

                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)
                val h = bitmap.height
                val w = bitmap.width

                // Draw key points and check posture
                val keyPoints = mutableListOf<FloatArray>()
                for (i in outputFeature0.indices step 3) {
                    if (outputFeature0[i + 2] > 0.2) {
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
                val posture = checkPosture(keyPoints, canvas)
                if (posture == "bad_sitting") {
                    badPostureCount++
                    if (badPostureCount >= 20) {
                        if (!mediaPlayer.isPlaying) {
                            executorService.submit { mediaPlayer.start() }
                        }
                    }
                } else {
                    badPostureCount = 0
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
    fun openCamera() {
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

    fun getPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) getPermissions()
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

    private fun checkPosture(keyPoints: List<FloatArray>, canvas: Canvas): String {
        val nose = keyPoints[0]
        val leftEye = keyPoints[1]
        val rightEye = keyPoints[2]
        val leftEar = keyPoints[3]
        val rightEar = keyPoints[4]
        val leftShoulder = keyPoints[5]
        val rightShoulder = keyPoints[6]
        val leftElbow = keyPoints[7]
        val rightElbow = keyPoints[8]
        val leftWrist = keyPoints[9]
        val rightWrist = keyPoints[10]
        val leftHip = keyPoints[11]
        val rightHip = keyPoints[12]
        val leftKnee = keyPoints[13]
        val rightKnee = keyPoints[14]
        val leftAnkle = keyPoints[15]
        val rightAnkle = keyPoints[16]

        // Check if key points for posture detection are available
        if (leftEar.any { it.isNaN() } && rightEar.any { it.isNaN() } || leftShoulder.any { it.isNaN() } &&
            rightShoulder.any { it.isNaN() } || leftHip.any { it.isNaN() } && rightHip.any { it.isNaN() } ||
            leftKnee.any { it.isNaN() } && rightKnee.any { it.isNaN() }) {
            Log.d("Undetected", "Not complete")
            return "good"
        }

        // Calculate angles and inclinations
        val neckInclination = calculateInclination(leftEar, leftShoulder)
        val torsoInclination = calculateInclination(leftShoulder, leftHip)
        val shoulderInclination = calculateInclination(leftShoulder, rightShoulder)
        val headTilt = calculateInclination(leftEar, rightEar)
        val hipInclination = calculateInclination(leftHip, rightHip)
        val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)

        // List to store drawn text positions to avoid overlap
        val drawnTextPositions = mutableListOf<Pair<Float, Float>>()

        // Draw angles on the canvas
        drawAngle(canvas, leftShoulder, leftEar, neckInclination, "Neck", drawnTextPositions)
        drawAngle(canvas, leftHip, leftShoulder, torsoInclination, "Torso", drawnTextPositions)
        drawAngle(canvas, leftShoulder, rightShoulder, shoulderInclination, "Shoulders", drawnTextPositions)
        drawAngle(canvas, leftHip, rightHip, hipInclination, "Hips", drawnTextPositions)
        drawAngle(canvas, leftEar, rightEar, headTilt, "Head Tilt", drawnTextPositions)
        drawAngle(canvas, leftElbow, leftShoulder, leftElbowAngle, "L-Elbow", drawnTextPositions)
        drawAngle(canvas, rightElbow, rightShoulder, rightElbowAngle, "R-Elbow", drawnTextPositions)
        drawAngle(canvas, leftKnee, leftHip, leftKneeAngle, "L-Knee", drawnTextPositions)
        drawAngle(canvas, rightKnee, rightHip, rightKneeAngle, "R-Knee", drawnTextPositions)

        // Check if it is a sitting posture
        val sittingPosture = isSittingPosture(leftHip, rightHip, leftKnee, rightKnee)

        if (!sittingPosture) {
            Log.d("Not_Sitting", "Not sitting")
            return "not_sitting"
        }

        Log.d("Neck", neckInclination.toString())
        Log.d("Terso", torsoInclination.toString())
        // Determine if sitting posture is good or bad
        return if (neckInclination in 85.0..95.0 && torsoInclination <= 100 && torsoInclination >= 80) "good_sitting" else "bad_sitting"
    }

    private fun isSittingPosture(leftHip: FloatArray, rightHip: FloatArray, leftKnee: FloatArray, rightKnee: FloatArray): Boolean {
        val leftHipKneeAngle = calculateAngle(leftHip, leftKnee, floatArrayOf(leftKnee[0], leftKnee[1] + 1))
        val rightHipKneeAngle = calculateAngle(rightHip, rightKnee, floatArrayOf(rightKnee[0], rightKnee[1] + 1))

        return leftHipKneeAngle in 70.0..150.0 || rightHipKneeAngle in 70.0..150.0
    }

    private fun calculateInclination(pointA: FloatArray, pointB: FloatArray): Float {
        val angle = Math.toDegrees(atan2((pointB[1] - pointA[1]).toDouble(), (pointB[0] - pointA[0]).toDouble())).toFloat()
        return if (angle < 0) angle + 360 else angle
    }

    private fun calculateAngle(pointA: FloatArray, pointB: FloatArray, pointC: FloatArray): Float {
        var angle = Math.toDegrees(atan2(pointC[1] - pointB[1], pointC[0] - pointB[0]).toDouble() -
                atan2(pointA[1] - pointB[1], pointA[0] - pointB[0]).toDouble()).toFloat()
        if (angle < 0) {
            angle += 360
        }
        return if (angle > 180) 360 - angle else angle

    }

    private fun drawAngle(canvas: Canvas, startPoint: FloatArray, endPoint: FloatArray, angle: Float, label: String, drawnTextPositions: MutableList<Pair<Float, Float>>) {
        val startX = startPoint[0]
        val startY = startPoint[1]
        val endX = endPoint[0]
        val endY = endPoint[1]

        // Draw the line between start and end points
        canvas.drawLine(startX, startY, endX, endY, paint)

        // Initial position for text
        var textX = startX + 10
        var textY = startY - 10

        // Avoid overlapping text
        val textHeight = paint.textSize
        val textWidth = paint.measureText("${angle.toInt()}° $label")

        var isOverlapping: Boolean
        var attempts = 0
        do {
            isOverlapping = false
            for (position in drawnTextPositions) {
                val distanceX = Math.abs(textX - position.first)
                val distanceY = Math.abs(textY - position.second)
                if (distanceX < textWidth && distanceY < textHeight) {
                    isOverlapping = true
                    textY += textHeight + 5 // Move the text down by text height + margin
                    break
                }
            }
            attempts++
        } while (isOverlapping && attempts < 10)

        paint.color = Color.RED
        canvas.drawText("${angle.toInt()}° $label", textX, textY, paint)
        paint.color = Color.YELLOW
        drawnTextPositions.add(Pair(textX, textY))
    }

    private fun drawSkeleton(canvas: Canvas, keyPoints: List<FloatArray>) {
        val skeleton = listOf(
            Pair(5, 6), Pair(5, 7), Pair(7, 9), Pair(6, 8), Pair(8, 10),
            Pair(5, 11), Pair(6, 12), Pair(11, 12), Pair(11, 13), Pair(13, 15),
            Pair(12, 14), Pair(14, 16)
        )
        for ((startIdx, endIdx) in skeleton) {
            val startPoint = keyPoints[startIdx]
            val endPoint = keyPoints[endIdx]
            if (!startPoint.any { it.isNaN() } && !endPoint.any { it.isNaN() }) {
                canvas.drawLine(startPoint[0], startPoint[1], endPoint[0], endPoint[1], paint)
            }
        }
    }
}
