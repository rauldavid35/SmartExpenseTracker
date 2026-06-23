package com.example.smartexpensetracker.repository

import android.content.Context
import com.example.smartexpensetracker.model.Dashboard
import com.example.smartexpensetracker.model.WidgetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


class DashboardRepository(context: Context, userId: String) {

    private val prefs = context.getSharedPreferences("dashboards_$userId", Context.MODE_PRIVATE)
    private val KEY = "dashboards_json"

    private val _dashboards = MutableStateFlow<List<Dashboard>>(load())
    val dashboards: Flow<List<Dashboard>> = _dashboards.asStateFlow()


    private fun load(): List<Dashboard> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val widgetsArr = obj.getJSONArray("widgets")
                val widgets = (0 until widgetsArr.length()).mapNotNull { j ->
                    try { WidgetType.valueOf(widgetsArr.getString(j)) } catch (_: Exception) { null }
                }
                Dashboard(
                    id      = obj.getString("id"),
                    name    = obj.getString("name"),
                    widgets = widgets
                )
            }
        } catch (_: Exception) { emptyList() }
    }


    private fun save(list: List<Dashboard>) {
        val arr = JSONArray()
        list.forEach { d ->
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            val wArr = JSONArray()
            d.widgets.forEach { wArr.put(it.name) }
            obj.put("widgets", wArr)
            arr.put(obj)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _dashboards.value = list
    }


    fun createDashboard(name: String, widgets: List<WidgetType>): Dashboard {
        val dashboard = Dashboard(
            id      = UUID.randomUUID().toString(),
            name    = name,
            widgets = widgets
        )
        save(_dashboards.value + dashboard)
        return dashboard
    }

    fun updateDashboard(dashboard: Dashboard) {
        val updated = _dashboards.value.map { if (it.id == dashboard.id) dashboard else it }
        save(updated)
    }

    fun deleteDashboard(id: String) {
        save(_dashboards.value.filter { it.id != id })
    }
}