package com.pigeonhub.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.common.HybridBinarizer

/** Small Camera2 + ZXing scanner so login never depends on a companion app. */
class QrScannerActivity : Activity() {

    companion object {
        const val EXTRA_PAYLOAD = "pigeonhub.qr_payload"
        private const val CAMERA_REQUEST = 4101
    }

    private lateinit var preview: TextureView
    private var camera: CameraDevice? = null
    private var reader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = TextureView(this)
        val root = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(this@QrScannerActivity).apply {
                text = "Point the camera at the PigeonHub login QR"
                setTextColor(0xffffffff.toInt())
                setBackgroundColor(0x99000000.toInt())
                setPadding(24, 18, 24, 18)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 48 })
        }
        setContentView(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        }
    }

    override fun onDestroy() {
        stopCamera()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
        else setResult(RESULT_CANCELED)
    }

    private fun startCamera() {
        cameraThread = HandlerThread("pigeonhub-qr-camera").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
        }
        if (preview.isAvailable) openCamera()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: return
        val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ available ->
            available.acquireLatestImage()?.use { decode(it) }
        }, cameraHandler)
        reader = imageReader
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                camera = device
                val texture = preview.surfaceTexture ?: return
                texture.setDefaultBufferSize(1280, 720)
                val previewSurface = Surface(texture)
                device.createCaptureSession(listOf(previewSurface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            addTarget(imageReader.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }.build()
                        session.setRepeatingRequest(request, null, cameraHandler)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) = Unit
                }, cameraHandler)
            }
            override fun onDisconnected(device: CameraDevice) { device.close(); camera = null }
            override fun onError(device: CameraDevice, error: Int) { device.close(); camera = null }
        }, cameraHandler)
    }

    private fun decode(image: Image) {
        if (handled) return
        val bytes = imageToNv21(image)
        val source = PlanarYUVLuminanceSource(bytes, image.width, image.height, 0, 0, image.width, image.height, false)
        val text = runCatching { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
        if (!text.isNullOrBlank()) {
            handled = true
            runOnUiThread {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PAYLOAD, text))
                finish()
            }
        }
    }

    private fun imageToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val output = ByteArray(width * height + width * height / 2)
        val y = image.planes[0]
        val u = image.planes[1]
        val v = image.planes[2]
        var offset = 0
        for (row in 0 until height) {
            for (column in 0 until width) {
                output[offset++] = y.buffer.get(row * y.rowStride + column * y.pixelStride)
            }
        }
        for (row in 0 until height / 2) {
            for (column in 0 until width / 2) {
                output[offset++] = v.buffer.get(row * v.rowStride + column * v.pixelStride)
                output[offset++] = u.buffer.get(row * u.rowStride + column * u.pixelStride)
            }
        }
        return output
    }

    private fun stopCamera() {
        camera?.close()
        camera = null
        reader?.close()
        reader = null
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }
}
