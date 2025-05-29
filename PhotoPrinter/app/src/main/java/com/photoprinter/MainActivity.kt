package com.photoprinter

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.photoprinter.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var currentPhoto: Bitmap? = null
    private lateinit var bluetoothAdapter: BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        binding.captureButton.setOnClickListener { takePhoto() }
        binding.printButton.setOnClickListener { printPhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupBluetooth()
    }

    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    currentPhoto = bitmap
                    binding.printButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "Foto capturada!", Toast.LENGTH_SHORT).show()
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Erro ao capturar foto", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun printPhoto() {
        if (currentPhoto == null) {
            Toast.makeText(this, "Nenhuma foto para imprimir", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Busca impressoras Bluetooth pareadas
            val printers = BluetoothPrintersConnections.selectFirstPaired()
            if (printers == null) {
                Toast.makeText(this, "Nenhuma impressora Bluetooth encontrada", Toast.LENGTH_SHORT).show()
                return
            }

            // Cria uma nova thread para impressão
            Thread {
                try {
                    // Conecta à impressora
                    val printer = EscPosPrinter(printers, 203, 48f, 32)
                    
                    // Prepara a imagem para impressão
                    val image = PrinterTextParserImg.bitmapToHexadecimalString(printer, currentPhoto!!)
                    
                    // Imprime a imagem
                    printer.printFormattedText(
                        "[C]<img>${image}</img>\n" +
                        "[C]================\n" +
                        "[C]Obrigado!\n" +
                        "[C]================\n\n\n\n"
                    )
                    
                    // Fecha a conexão
                    printer.disconnectPrinter()
                    
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Impressão concluída!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Erro ao imprimir: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao conectar à impressora: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Erro ao iniciar câmera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
} 