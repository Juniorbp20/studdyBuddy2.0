package com.example.studybuddy.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.studybuddy.R
import com.example.studybuddy.data.ServiceLocator
import com.example.studybuddy.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TodayTasksWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodayTasksFactory(applicationContext)
}

class TodayTasksFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<com.example.studybuddy.model.Task> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        tasks = runBlocking {
            val start = DateUtils.startOfToday()
            val end = start + 24 * 60 * 60 * 1000
            ServiceLocator.getRepository(context)
                .tasksInRange(start, end)
                .first()
                .filter { !it.isCompleted }
                .take(5)
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.item_widget_task)
        views.setTextViewText(R.id.widget_task_title, task.title)
        views.setTextViewText(R.id.widget_task_time, DateUtils.formatTime(task.dueDate))

        val fillInIntent = Intent()
        fillInIntent.putExtra("task_id", task.id)
        views.setOnClickFillInIntent(R.id.widget_task_root, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun hasStableIds(): Boolean = false

    override fun getItemId(position: Int): Long = tasks[position].id.toLong()
}