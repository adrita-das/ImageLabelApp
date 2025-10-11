package com.example.imagelabelapp

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.ImageView
import com.bumptech.glide.Glide

class FeedbackAdapter(private val entries: List<Entry>) :
    RecyclerView.Adapter<FeedbackAdapter.FeedbackViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    class FeedbackViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val image: ImageView = view.findViewById(R.id.itemImage)
        val label: TextView = view.findViewById(R.id.itemLabel)
        val timestamp: TextView = view.findViewById(R.id.itemTimestamp)
        val path: TextView = view.findViewById(R.id.itemPath)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedbackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.entry, parent, false)
        return FeedbackViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedbackViewHolder, position: Int) {
        val entry = entries[position]

        val time = dateFormat.format(Date(entry.timestamp))
        val fileName = entry.imageFilePath.substringAfterLast("/")

        val imageUri = Uri.parse(entry.imageFilePath)

        holder.label.text = "Correct Label: ${entry.correctLabel}"
        holder.timestamp.text = "Time: $time"
        holder.path.text = "Image URI: $fileName"

        Glide.with(holder.itemView.context)
            .load(imageUri)
            .into(holder.image)


    }

    override fun getItemCount() = entries.size
}