package com.example.teamtotest.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.teamtotest.R
import com.example.teamtotest.activity.FileActivity
import com.example.teamtotest.dto.FileDTO
import kotlinx.android.synthetic.main.item_file_dashboard.view.*
import kotlin.collections.ArrayList

class FileRVAdapterMain(
    private val context: Context,
    private var fileDTO: ArrayList<FileDTO>
) :
    RecyclerView.Adapter<ViewHolderHelper>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderHelper {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_file_dashboard, parent, false)
        return ViewHolderHelper(view)
    }

    override fun getItemCount(): Int {
        return fileDTO.size
    }

    override fun onBindViewHolder(holder: ViewHolderHelper, position: Int) {
        holder.itemView.d_file_project_name.text = fileDTO[position].projectdata!!.projectName
        holder.itemView.d_file_name.text = fileDTO[position].fileName

        holder.itemView.setOnClickListener{
            val intent = Intent(context, FileActivity::class.java)
            intent.putExtra("PID", fileDTO[position].projectdata!!.pid)
            context.startActivity(intent)
        }
    }


}
