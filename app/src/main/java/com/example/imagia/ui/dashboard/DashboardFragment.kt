package com.example.imagia.ui.dashboard

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imagia.databinding.FragmentDashboardBinding
import com.example.imagia.databinding.HistoryItemBinding
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    data class HistoryItem(val id: Int, val prompt: String, val model: String, val createdAt: String)

    inner class HistoryAdapter(private var items: List<HistoryItem>) :
        RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        inner class HistoryViewHolder(val binding: HistoryItemBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val binding = HistoryItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return HistoryViewHolder(binding)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                // Se muestra la fecha formateada
                textDate.text = formatDate(item.createdAt)
                textPrompt.text = item.prompt

                // Al hacer clic sobre el item se alterna la visibilidad del prompt
                root.setOnClickListener {
                    textPrompt.visibility =
                        if (textPrompt.visibility == View.GONE) View.VISIBLE else View.GONE
                }
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateItems(newItems: List<HistoryItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(context)
        historyAdapter = HistoryAdapter(emptyList())
        binding.recyclerViewHistory.adapter = historyAdapter

        fetchHistory()

        return root
    }

    private fun fetchHistory() {
        Thread {
            try {
                val sharedPreferences =
                    requireContext().getSharedPreferences("USER_DATA", Context.MODE_PRIVATE)
                val nickname = sharedPreferences.getString("nickname", "default")
                val apiToken = sharedPreferences.getString("apiToken", "default")

                val url = URL("https://imagia3.ieti.site/api/usuaris/historial/prompts?usuario=$nickname")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $apiToken")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        responseBuilder.append(line)
                    }
                    reader.close()
                    val responseString = responseBuilder.toString()
                    Log.d("Peticion", responseString)

                    val jsonResponse = JSONObject(responseString)
                    val dataArray = jsonResponse.getJSONArray("data")
                    val historyList = mutableListOf<HistoryItem>()
                    for (i in 0 until dataArray.length()) {
                        val itemObj = dataArray.getJSONObject(i)
                        val id = itemObj.getInt("id")
                        val prompt = itemObj.getString("prompt")
                        val model = itemObj.getString("model")
                        val createdAt = itemObj.getString("createdAt")
                        historyList.add(HistoryItem(id, prompt, model, createdAt))
                    }

                    activity?.runOnUiThread {
                        historyAdapter.updateItems(historyList)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    /**
     * Función auxiliar para formatear la fecha.
     * Se asume que la fecha de entrada viene en formato ISO 8601, por ejemplo: "yyyy-MM-dd'T'HH:mm:ss'Z'".
     * Se formatea a "dd/MM/yyyy HH:mm".
     */
    private fun formatDate(dateString: String): String {
        return try {
            // Ajusta el formato de entrada según tu API
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(dateString)
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            // En caso de error, se retorna la cadena original
            dateString
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
