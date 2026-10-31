package com.example.almusapptest

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this)
        view.text = "Hello from almus app test"
        view.textSize = 24f
        view.setPadding(48, 96, 48, 48)
        setContentView(view)
    }
}
