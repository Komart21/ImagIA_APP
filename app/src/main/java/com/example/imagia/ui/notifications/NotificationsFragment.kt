package com.example.imagia.ui.notifications

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.imagia.databinding.FragmentNotificationsBinding
import org.json.JSONObject
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val notificationsViewModel =
            ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Referencias a los TextView
        val textView: TextView = binding.textDashboard
        val userNameTextView: TextView = binding.textUserName

        // Observa la ViewModel, si la usas para algo adicional
        notificationsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        // Obtén el nickname de SharedPreferences
        val sharedPreferences =
            requireContext().getSharedPreferences("USER_DATA", Context.MODE_PRIVATE)
        val nickname = sharedPreferences.getString("nickname", "Usuario")

        // Muestra el nickname en textUserName
        userNameTextView.text = "Usuario: " + nickname

        // Realiza la petición GET para obtener la cuota
        realizarPeticionGet(nickname)

        return root
    }

    private fun realizarPeticionGet(nickname: String?) {
        // Ejecuta la petición en un único hilo de fondo para no bloquear la UI
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val sharedPreferences =
                    requireContext().getSharedPreferences("USER_DATA", Context.MODE_PRIVATE)

                // Construye la URL con el parámetro 'usuario'
                val url = URL(
                    "https://imagia3.ieti.site/api/usuaris/quota?usuario=${
                        nickname ?: "default"
                    }"
                )
                val connection = url.openConnection() as HttpURLConnection

                // Configura la petición GET
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer ${sharedPreferences.getString("apiToken", "default")}"
                )

                // Conectamos
                connection.connect()

                // Obtenemos el código de respuesta
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Leemos la respuesta
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("Petición GET", "Respuesta: $response")

                    // Procesamos la respuesta (asumiendo que es un JSON)
                    val jsonObject = JSONObject(response)
                    Log.d("Respuesta", jsonObject.toString())

                    // Supongamos que en la respuesta viene algo como:
                    // {
                    //   "ok": true,
                    //   "data": {
                    //       "quota": 123
                    //   }
                    // }
                    // Ajusta según tu estructura real de JSON
                    val data = jsonObject.optJSONObject("data")
                    val quota = data?.optString("quota_disponible", "No disponible") ?: "No disponible"

                    // Actualizar la UI (TextView) con la información obtenida
                    requireActivity().runOnUiThread {
                        binding.textDashboard.text =
                            "Tu cuota disponible es: $quota"
                    }
                } else {
                    Log.e("Petición GET", "Error en la petición: Código $responseCode")
                }

            } catch (e: Exception) {
                Log.e("Petición GET", "Error en la petición GET", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
