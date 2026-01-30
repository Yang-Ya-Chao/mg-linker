package com.my.mg.util


import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.my.mg.data.FuelRecord

object FuelRecordStore {

    private const val KEY = "fuel_records"
    private val gson = Gson()

    fun load(context: Context): MutableList<FuelRecord> {
        val prefs = context.getSharedPreferences("mg_config", Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<FuelRecord>>() {}.type
        return gson.fromJson(json, type)
    }

    fun save(context: Context, list: List<FuelRecord>) {
        val prefs = context.getSharedPreferences("mg_config", Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }
}
