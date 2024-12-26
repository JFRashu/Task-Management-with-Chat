package com.jfrashu.taskchat.taskactivities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private var tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val taskTitle: TextView = view.findViewById(R.id.taskTitle)
        val taskDescription: TextView = view.findViewById(R.id.taskDescription)
        val taskStatus: Chip = view.findViewById(R.id.taskStatus)
        val lastMessage: TextView = view.findViewById(R.id.lastMessage)
        val lastActivityTime: TextView = view.findViewById(R.id.lastActivityTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        holder.taskTitle.text = task.title
        holder.taskDescription.text = task.description

        // Set status chip style based on task status
        holder.taskStatus.text = when (task.status) {
            "pending" -> "Pending"
            "in_progress" -> "In Progress"
            "completed" -> "Completed"
            else -> task.status.capitalize()
        }

        // Set chip color based on status
        holder.taskStatus.setChipBackgroundColorResource(
            when (task.status) {
                "pending" -> R.color.pending_status
                "in_progress" -> R.color.in_progress_status
                "completed" -> R.color.completed_status
                else -> R.color.default_status
            }
        )

        // Format last message
        holder.lastMessage.text = if (task.lastMessage.isNotEmpty()) {
            task.lastMessage
        } else {
            "No messages yet"
        }

        // Format last activity time
        val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        holder.lastActivityTime.text = dateFormat.format(Date(task.lastActivity))

        // Set click listener
        holder.itemView.setOnClickListener { onTaskClick(task) }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}