package com.lemonkids.kidtask.feature.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 仅保存首页待完成任务的顺序；任务内容仍以服务端快照为准。 */
internal class HomeTaskOrderStore(context: Context) {
    private val preferences = context.getSharedPreferences("home_pending_task_order", Context.MODE_PRIVATE)

    private fun key(childId: String, date: String) = "$childId:$date"

    fun read(childId: String, date: String): List<String>? {
        val raw = preferences.getString(key(childId, date), null) ?: return null
        return runCatching {
            val data = JSONObject(raw)
            if (data.getInt("version") != 1) return@runCatching null
            val array = data.getJSONArray("pendingIds")
            (0 until array.length()).map { array.getString(it) }.distinct()
        }.getOrNull()
    }

    fun write(childId: String, date: String, ids: List<String>): Boolean {
        val data = JSONObject()
            .put("version", 1)
            .put("pendingIds", JSONArray(ids.distinct()))
        return preferences.edit().putString(key(childId, date), data.toString()).commit()
    }
}
