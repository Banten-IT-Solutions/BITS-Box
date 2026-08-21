package id.bits.box.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.king.zxing.util.CodeUtils
import id.bits.box.R
import id.bits.box.database.DataStore
import id.bits.box.database.ProfileManager
import id.bits.box.databinding.LayoutScannerBinding
import id.bits.box.group.RawUpdater
import id.bits.box.ktx.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


class ScannerActivity : ThemedActivity() {

    lateinit var binding: LayoutScannerBinding

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startCamera()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 25) getSystemService<ShortcutManager>()!!.reportShortcutUsed("scan")
        binding = LayoutScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.ivFlashlight.setOnClickListener { toggleTorchState() }

        // Observe torch state to keep the flashlight indicator in sync
        camera?.cameraInfo?.torchState?.observe(this) { state ->
            binding.ivFlashlight.isSelected = state == TorchState.ON
        }

        startCamera()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scanner_menu, menu)
        return true
    }

    val importCodeFile = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        runOnDefaultDispatcher {
            try {
                it.forEachTry { uri ->
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(
                                contentResolver, uri
                            )
                        ) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(
                            contentResolver, uri
                        )
                    }
                    val result = CodeUtils.parseCodeResult(bitmap)
                    onMainDispatcher {
                        onScanResultCallback(result, true)
                    }
                }
                finish()
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    Toast.makeText(app, e.readableMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_import_file) {
            startFilesForResult(importCodeFile, "image/*")
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    var finished = AtomicBoolean(false)
    var importedN = AtomicInteger(0)

    fun onScanResultCallback(result: Result?, multi: Boolean): Boolean {
        if (!multi && finished.getAndSet(true)) return true
        if (!multi) finish()
        runOnDefaultDispatcher {
            try {
                val text = result?.text ?: throw Exception("QR code not found")
                val results = RawUpdater.parseRaw(text)
                if (!results.isNullOrEmpty()) {
                    val currentGroupId = DataStore.selectedGroupForImport()
                    if (DataStore.selectedGroup != currentGroupId) {
                        DataStore.selectedGroup = currentGroupId
                    }

                    for (profile in results) {
                        ProfileManager.createProfile(currentGroupId, profile)
                        importedN.addAndGet(1)
                    }
                } else {
                    onMainDispatcher {
                        Toast.makeText(app, R.string.action_import_err, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SubscriptionFoundException) {
                startActivity(Intent(this@ScannerActivity, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = e.link.toUri()
                })
            } catch (e: Throwable) {
                Logs.w(e)
                onMainDispatcher {
                    var text = getString(R.string.action_import_err)
                    text += "\n" + e.readableMessage
                    Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(this), createAnalyzer())
            }
        provider.unbindAll()
        camera = provider.bindToLifecycle(
            this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
        )
        binding.ivFlashlight.isSelected =
            camera?.cameraInfo?.torchState?.value == TorchState.ON
    }

    private fun createAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            try {
                if (!finished.get()) {
                    decodeQr(imageProxy)?.let { result ->
                        imageAnalysis?.clearAnalyzer()
                        onScanResultCallback(result, false)
                    }
                }
            } catch (e: Throwable) {
                // ignore frames without a QR code
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun decodeQr(imageProxy: ImageProxy): Result? {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val source = PlanarYUVLuminanceSource(
            data,
            imageProxy.width,
            imageProxy.height,
            0, 0,
            imageProxy.width,
            imageProxy.height,
            false
        )
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
    }

    private fun releaseCamera() {
        cameraProvider?.unbindAll()
        camera = null
    }

    private fun toggleTorchState() {
        val cam = camera ?: return
        val enabled = cam.cameraInfo.torchState.value == TorchState.ON
        cam.cameraControl.enableTorch(!enabled)
    }

    override fun onDestroy() {
        releaseCamera()
        super.onDestroy()
        if (importedN.get() > 0) {
            var text = "${getString(R.string.action_import_msg)}\n${importedN.get()} profile(s)"
            Toast.makeText(app, text, Toast.LENGTH_LONG).show()
        }
    }
}