package com.wonderfood.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(56, 72, 56, 56)
            setBackgroundColor(Color.rgb(250, 248, 241))
        }
        val title = TextView(this).apply {
            text = getString(R.string.health_connect_rationale_title)
            textSize = 28f
            setTextColor(Color.rgb(31, 36, 28))
            setPadding(0, 0, 0, 24)
        }
        val body = TextView(this).apply {
            text = getString(R.string.health_connect_rationale_body)
            textSize = 17f
            setLineSpacing(8f, 1f)
            setTextColor(Color.rgb(84, 82, 75))
            setPadding(0, 0, 0, 32)
        }
        val done = Button(this).apply {
            text = getString(R.string.done)
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(body)
        root.addView(done)
        setContentView(root)
    }
}
