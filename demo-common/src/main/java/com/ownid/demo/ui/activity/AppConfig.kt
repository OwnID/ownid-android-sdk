package com.ownid.demo.ui.activity

import android.content.Context
import android.content.SharedPreferences

object AppConfig {
    private lateinit var pref: SharedPreferences

    fun init(context: Context) {
        pref = context.getSharedPreferences("config.xml", Context.MODE_PRIVATE)
    }
}