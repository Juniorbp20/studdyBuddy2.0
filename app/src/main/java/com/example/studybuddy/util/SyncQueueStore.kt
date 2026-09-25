package com.example.studybuddy.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SyncQueueItem(
    val id: String,
    val entityType: String,
    val action: String,
    val entityId: String,
    val payloadJson: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("entityType", entityType)
            put("action", action)
            put("entityId", entityId)
            put("payloadJson", payloadJson)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncQueueItem {
            return SyncQueueItem(
                id = json.optString("id", ""),
                entityType = json.optString("entityType", ""),
                action = json.optString("action", ""),
                entityId = json.optString("entityId", ""),
                payloadJson = json.optString("payloadJson", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

object SyncQueueStore {

    private const val PREFS_NAME = "studybuddy_sync_queue"
    private const val KEY_ITEMS = "queue_items"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"
    private const val KEY_SYNC_STATUS = "sync_status"

    const val STATUS_IDLE = "IDLE"
    const val STATUS_SYNCING = "SYNCING"
    const val STATUS_OFFLINE = "OFFLINE"
    const val STATUS_SYNCED = "SYNCED"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(context: Context, entityType: String, action: String, entityId: String, payload: String = "") {
        val list = getPendingItems(context).toMutableList()
        // Deduplicate or update existing pending change for the same entity if applicable
        list.removeAll { it.entityType == entityType && it.entityId == entityId && it.action == action }
        val newItem = SyncQueueItem(
            id = "${entityType}_${entityId}_${System.currentTimeMillis()}",
            entityType = entityType,
            action = action,
            entityId = entityId,
            payloadJson = payload,
            timestamp = System.currentTimeMillis()
        )
        list.add(newItem)
        saveItems(context, list)
    }

    @Synchronized
    fun getPendingItems(context: Context): List<SyncQueueItem> {
        val raw = prefs(context).getString(KEY_ITEMS, "[]") ?: "[]"
        val result = mutableListOf<SyncQueueItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                result.add(SyncQueueItem.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
        return result
    }

    @Synchronized
    fun clearProcessed(context: Context, processedIds: List<String>) {
        val current = getPendingItems(context).toMutableList()
        current.removeAll { processedIds.contains(it.id) }
        saveItems(context, current)
    }

    @Synchronized
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_ITEMS).apply()
    }

    private fun saveItems(context: Context, items: List<SyncQueueItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun getPendingCount(context: Context): Int {
        return getPendingItems(context).size
    }

    fun setLastSyncTimestamp(context: Context, timestamp: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    fun getLastSyncTimestamp(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L)
    }

    fun setSyncStatus(context: Context, status: String) {
        prefs(context).edit().putString(KEY_SYNC_STATUS, status).apply()
    }

    fun getSyncStatus(context: Context): String {
        return prefs(context).getString(KEY_SYNC_STATUS, STATUS_IDLE) ?: STATUS_IDLE
    }
}
