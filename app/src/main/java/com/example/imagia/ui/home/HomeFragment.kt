package com.example.imagia.ui.home

import android.speech.tts.TextToSpeech
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.imagia.databinding.FragmentHomeBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HomeFragment : Fragment() {

    lateinit var tts : TextToSpeech;
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasAllPermissions()) {
            initializeCamera()
        } else {
            requestPermissions(PERMISSIONS, PERMISSION_CODE)
        }

        binding.captureButton.setOnClickListener {
            takePhoto() // Devolver texto para hablar
            // Aquí va el tts
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root


    }


    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
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
                Log.e(TAG, "Error al enlazar la cámara: ", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun sendPhotoToServer(filePath: Uri?){
        val client = OkHttpClient()

        val inputStream = requireContext().contentResolver.openInputStream(filePath!!)
        val fileBytes = inputStream?.readBytes() ?: ByteArray(0)
        inputStream?.close()

        val base64String = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

        val jsonObject = JSONObject().apply {
            put("prompt", "Describe esta imagen en español por favor")
            put("stream", false)
            put("model", "llama3.2-vision")

            val jsonArray = JSONArray().apply {
                put(base64String)
            }
            put("images", jsonArray)
        }

        val mediaType = "application/json; charset=UTF-8".toMediaTypeOrNull()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)

        // 4) Construir la request OkHttp
        val request = Request.Builder()
            .url("https://imagia3.ieti.site/api/analitzar-imatge")
            .post(requestBody)
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Error de red, no hubo respuesta exitosa
                println("Error al subir la imagen: ${e.message}")
                // Si estás en Android, aquí puedes usar runOnUiThread o LiveData/Flow para notificar al UI
            }

            override fun onResponse(call: Call, response: Response) {
                // Respuesta exitosa o con error del servidor
                response.use {
                    if (!response.isSuccessful) {
                        
                    } else {

                        println("Imagen subida con éxito: ${response.code}")
                    }
                }
            }
        })
    }


    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(
            FILENAME_PATTERN, Locale.US
        ).format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/CameraX-Image"
                )
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Error al tomar la foto: ${exc.message}", exc)
                    Toast.makeText(
                        requireContext(),
                        "Error al tomar la foto.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Foto guardada: ${outputFileResults.savedUri}"
                    Log.d(TAG, msg)
                    Toast.makeText(
                        requireContext(),
                        msg,
                        Toast.LENGTH_SHORT
                    ).show()

                    sendPhotoToServer(outputFileResults.savedUri)
                }
            }
        )
    }


    private fun hasAllPermissions(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                requireContext(), it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeCamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Los permisos de cámara son necesarios.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val PERMISSION_CODE = 10
        private const val FILENAME_PATTERN = "yyyy-MM-dd-HH-mm-ss-SSS"

        // Solo permisos de CAMERA; si requieres otros, agrégalos aquí.
        private val PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).apply {
            // Para versiones <= Pie (Android 9)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}
