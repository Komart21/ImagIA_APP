package com.example.imagia.login

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.imagia.databinding.ActivityLoginBinding
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    // ViewBinding: se genera la clase ActivityLoginBinding a partir de activity_login.xml
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Infla el layout usando ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Manejo del click en el botón de Login
        binding.buttonLogin.setOnClickListener {
            val nickname = binding.editNickname.text.toString().trim()
            val email = binding.editEmail.text.toString().trim()
            val phone = binding.editPhone.text.toString().trim()

            // Verifica que los campos no estén vacíos
            if (nickname.isNotEmpty() && email.isNotEmpty() && phone.isNotEmpty()) {
                hideKeyboard()
                // Envía los datos al servidor (similar a tu NotificationsFragment)
                sendLoginData(nickname, email, phone)
            } else {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun sendLoginData(nickname: String, email: String, phone: String) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                // Crea el JSON con los campos que tu API espera
                val jsonObject = JSONObject().apply {
                    put("nickname", nickname)
                    put("email", email)
                    put("telefon", phone)
                }

                // Conexión HTTP a tu endpoint (ajusta la URL según sea necesario)
                val url = URL("https://imagia3.ieti.site/api/usuaris/registrar")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true

                // Envía el JSON en el body de la petición
                DataOutputStream(connection.outputStream).use { output ->
                    output.writeBytes(jsonObject.toString())
                }

                // Lee la respuesta
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("Petición", "Respuesta recibida: $response")
                    // Aquí podrías parsear la respuesta, guardar token, etc.

                    // En caso de éxito, podrías ir a otra pantalla, ejemplo:
                    // runOnUiThread {
                    //     startActivity(Intent(this, NextActivity::class.java))
                    //     finish()
                    // }

                } else {
                    Log.d("Petición", "Error en la petición: Código $responseCode")
                }
            } catch (e: Exception) {
                Log.e("Petición", "Error al enviar la información", e)
            }
        }
    }
}
