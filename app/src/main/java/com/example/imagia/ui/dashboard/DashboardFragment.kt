package com.example.imagia.ui.dashboard

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imagia.databinding.FragmentDashboardBinding
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    data class HistoryItem(val id: Int, val prompt: String, val model: String, val createdAt: String)

    inner class HistoryAdapter(private var items: List<HistoryItem>) :
        RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        inner class HistoryViewHolder(
            itemView: View,
            val textDate: TextView,
            val textPrompt: TextView
        ) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val context = parent.context

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val textDate = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(null, Typeface.BOLD)
            }
            val textPrompt = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                visibility = View.GONE
            }
            layout.addView(textDate)
            layout.addView(textPrompt)

            return HistoryViewHolder(layout, textDate, textPrompt)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = items[position]
            holder.textDate.text = item.createdAt
            holder.textPrompt.text = item.prompt

            holder.itemView.setOnClickListener {
                if (holder.textPrompt.visibility == View.GONE) {
                    holder.textPrompt.visibility = View.VISIBLE
                } else {
                    holder.textPrompt.visibility = View.GONE
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
                val url = URL("https://imagia3.ieti.site/api/usuaris/historial/prompts?usuario=${requireContext().getSharedPreferences("USER_DATA", Context.MODE_PRIVATE).getString("nickname", "default")}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer ${requireContext().getSharedPreferences("USER_DATA", Context.MODE_PRIVATE).getString("apiToken", "default")}")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
