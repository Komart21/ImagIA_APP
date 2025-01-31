package com.example.imagia.ui.notifications

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.imagia.databinding.FragmentNotificationsBinding
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.buttonLogin.setOnClickListener {
            val nickname = binding.editNickname.text.toString().trim()
            val email = binding.editEmail.text.toString().trim()
            val phone = binding.editPhone.text.toString().trim()

            if (nickname.isNotEmpty() && email.isNotEmpty() && phone.isNotEmpty()) {
                hideKeyboard()
                sendLoginData(nickname, email, phone)
            } else {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hideKeyboard() {
        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun sendLoginData(phone: String, nickname: String, email:String) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val jsonObject = JSONObject().apply {
                    put("nickname", nickname)
                    put("email", email)
                    put("telefon", phone)
                }

                val url = URL("https://imagia3.ieti.site/api/usuaris/registrar")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true

                DataOutputStream(connection.outputStream).use { it.writeBytes(jsonObject.toString()) }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("Petición", "Respuesta recibida: $response")
                } else {
                    Log.d("Petición", "Error en la petición: Código $responseCode")
                }
            } catch (e: Exception) {
                Log.e("Petición", "Error al enviar la imagen", e)
            }
        }
    }
}
