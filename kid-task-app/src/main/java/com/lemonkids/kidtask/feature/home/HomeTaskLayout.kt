package com.lemonkids.kidtask.feature.home

import com.lemonkids.kidtask.ui.components.TaskUiItem

/** 首页展示派生数据；只重排 UI，不改变仓库中的任务状态与顺序。 */
data class HomeTaskLayout(
    val pending: List<TaskUiItem>,
    val completed: List<TaskUiItem>,
    val progressPercent: Int
)

fun buildHomeTaskLayout(tasks: List<TaskUiItem>, categoryOrder: List<String>): HomeTaskLayout {
    val completed = tasks.filter { it.status == "DONE" || it.status == "VERIFIED" }
    val pending = tasks.filterNot { it.status == "DONE" || it.status == "VERIFIED" }
        .withIndex()
        .sortedWith(compareBy({ indexed ->
            val position = categoryOrder.indexOf(indexed.value.category)
            if (position < 0) Int.MAX_VALUE else position
        }, { it.index }))
        .map { it.value }
    return HomeTaskLayout(
        pending = pending,
        completed = completed,
        progressPercent = if (tasks.isEmpty()) 0 else completed.size * 100 / tasks.size
    )
}
