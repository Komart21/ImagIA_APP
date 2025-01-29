package com.example.imagia.ui.home

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.imagia.databinding.FragmentHomeBinding
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HomeFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    // CameraX
    private var imageCapture: ImageCapture? = null

    // Executor para realizar tareas en segundo plano (en lugar de bloquear la UI)
    private lateinit var cameraExecutor: ExecutorService

    // TTS
    private lateinit var tts: TextToSpeech

    // Lanzador para pedir múltiples permisos
    private val multiplePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "No se otorgaron todos los permisos", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // (No recomendado en producción) Permitimos operaciones de red en el hilo principal,
        // pero de todos modos haremos la subida en un hilo aparte.
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Inicializar TTS
        tts = TextToSpeech(requireContext(), this)

        // Verificar permisos
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Botón para capturar
        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        // Creamos un Executor de un solo hilo para tareas en background
        cameraExecutor = Executors.newSingleThreadExecutor()

        return binding.root
    }

    /** Método de inicialización de TTS */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("HomeFragment", "El idioma no es soportado.")
            } else {
                Log.i("HomeFragment", "TTS inicializado correctamente.")
            }
        } else {
            Log.e("HomeFragment", "Error al inicializar TTS.")
        }
    }

    /** Verificar si tenemos todos los permisos */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    /** Solicitar permisos */
    private fun requestPermissions() {
        multiplePermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    /** Iniciar la cámara con CameraX */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e("HomeFragment", "Error al vincular casos de uso de cámara", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Captura la foto y la guarda en MediaStore */
    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Demo")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireContext().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        // Toma la foto
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("HomeFragment", "Error al guardar la foto: ${exc.message}", exc)
                    Toast.makeText(requireContext(), "Error al capturar foto", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    Toast.makeText(requireContext(), "Foto guardada: $uri", Toast.LENGTH_SHORT).show()
                    Log.d("HomeFragment", "URI de la foto: $uri")

                    // Obtener bytes de la imagen
                    uri?.let { safeUri ->
                        val imageBytes = requireContext().contentResolver.openInputStream(safeUri)?.use { it.readBytes() }
                        if (imageBytes != null) {
                            // En vez de subir en el hilo principal, lo hacemos en un hilo de fondo
                            cameraExecutor.execute {
                                // Subir la imagen con HttpURLConnection (bloqueante)
                                val serverResponse = doBlockingUpload(imageBytes)

                                // Volver al hilo principal para interactuar con TTS o UI
                                requireActivity().runOnUiThread {
                                    tts.speak(serverResponse, TextToSpeech.QUEUE_FLUSH, null, null)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    /**
     * Realiza la subida de la imagen al servidor de forma bloqueante usando HttpURLConnection.
     * Retorna el texto de respuesta del servidor (o un mensaje de error).
     */
    private fun doBlockingUpload(imageBytes: ByteArray): String {
        val boundary = "Boundary-${System.currentTimeMillis()}"
        val postUrl = "http://10.0.2.2:3000/api/images/image"  // Ajusta según tu servidor/host real

        return try {
            val url = URL(postUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000  // 10 segundos
            connection.readTimeout = 10_000     // 10 segundos
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            DataOutputStream(connection.outputStream).use { outputStream ->
                val lineEnd = "\r\n"
                val twoHyphens = "--"

                // Encabezado de la parte "form-data"
                outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                outputStream.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"captured.jpg\"$lineEnd")
                outputStream.writeBytes("Content-Type: image/jpeg$lineEnd")
                outputStream.writeBytes(lineEnd)

                // Contenido de la imagen
                outputStream.write(imageBytes)
                outputStream.writeBytes(lineEnd)

                // Cierre de la parte
                outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                outputStream.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    Log.d("HomeFragment", "Respuesta del servidor: $response")
                    response.toString()
                }
            } else {
                val errorMsg = "Error del servidor: $responseCode ${connection.responseMessage}"
                Log.e("HomeFragment", errorMsg)
                errorMsg
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error al subir la imagen: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cerrar el Executor
        cameraExecutor.shutdown()

        // Detener TTS si está activo
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }

    companion object {
        // Lista de permisos
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            // Agrega RECORD_AUDIO si capturas audio o WRITE_EXTERNAL_STORAGE si < Android 10, etc.
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}
