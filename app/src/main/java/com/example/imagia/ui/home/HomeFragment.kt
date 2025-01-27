package com.example.imagia.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.imagia.R
import com.example.imagia.databinding.FragmentHomeBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    private lateinit var imageCapture: ImageCapture
    private var outputDirectory: File? = null

    // Lanzador para solicitar permiso de cámara
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Directorio donde guardamos la foto temporalmente
        outputDirectory = requireContext().cacheDir // Ej: /data/user/0/tu.pack/cache

        // Pedir permiso de cámara
        requestCameraPermission()

        // Botón para tomar foto
        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        return root
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permiso ya concedido
                startCamera()
            }
            else -> {
                // Solicitar el permiso
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()
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
                Toast.makeText(
                    requireContext(),
                    "Error al iniciar la cámara: ${exc.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        // Verificamos si imageCapture está inicializado
        val imageCapture = this.imageCapture ?: return

        // Nombre de archivo (puedes cambiar la ruta o el directorio)
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        // Opciones para guardar la foto
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Aquí ya tenemos la foto guardada en photoFile
                    Toast.makeText(
                        requireContext(),
                        "Foto capturada: ${photoFile.absolutePath}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Convertimos el archivo en ByteArray para enviarlo al servidor
                    val imageBytes = photoFile.readBytes()

                    val imageBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)

                    // uploadImageToServer(imageBytes)

                    Log.d("HomeFragment", "Tamaño de la foto en bytes: ${imageBytes.size}")

                    // Si NO quieres conservar el archivo localmente, puedes borrarlo:
                    // photoFile.delete()
                }



                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        "Error al capturar la foto: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
