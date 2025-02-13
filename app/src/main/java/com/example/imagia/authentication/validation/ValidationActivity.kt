package com.example.imagia.authentication.validation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.imagia.MainActivity
import com.example.imagia.databinding.ActivityValidationBinding
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ValidationActivity: AppCompatActivity()  {

    private lateinit var binding: ActivityValidationBinding

    private lateinit var nickname: String
    private lateinit var email: String
    private lateinit var phone: String
    private lateinit var apiToken: String

    override fun onCreate(savedInstanceState: Bundle?) {

        nickname = intent.getStringExtra("nickname").toString()
        email = intent.getStringExtra("email").toString()
        phone = intent.getStringExtra("phone").toString()

        super.onCreate(savedInstanceState)
        // Infla el layout usando ViewBinding
        binding = ActivityValidationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Manejo del click en el botón de Login
        binding.buttonValidate.setOnClickListener {
            val validationCode = binding.editValidationCode.text.toString().trim()
            // Verifica que los campos no estén vacíos
            if (validationCode.isNotEmpty()) {
                hideKeyboard()
                // Envía los datos al servidor (similar a tu NotificationsFragment)
                sendValidationData(validationCode)
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
    private fun sendValidationData(validationCode: String) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                // Crea el JSON con los campos que tu API espera
                val jsonObject = JSONObject().apply {
                    put("codi_validacio", validationCode)
                    put("telefon", phone)
                }

                // Conexión HTTP a tu endpoint (ajusta la URL según sea necesario)
                val url = URL("https://imagia3.ieti.site/api/usuaris/validar")
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
                    apiToken = JSONObject(response).get("apiToken").toString()
                    val sharedPreference =  getSharedPreferences("USER_DATA",Context.MODE_PRIVATE)
                    var editor = sharedPreference.edit()
                    editor.putString("nickname",nickname)
                    editor.putString("apiToken", apiToken)
                    editor.apply()
                    runOnUiThread {
                        intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    Log.d("Petición", "Error en la petición: Código $responseCode")
                }
            } catch (e: Exception) {
                Log.e("Petición", "Error al enviar la información", e)
            }
        }
    }
}