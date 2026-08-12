package com.lemonkids.kidmonitor.monitor

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class LimitBlockActivity : Activity() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_NOTIFY_ID = "notify_id"
        private const val TAG = "LimitBlock"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate")

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "该休息一下啦"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "先放下平板，做点别的事情吧"
        val notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, 2003)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(220, 0, 0, 0))
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true // 拦截触摸
        }

        val icon = TextView(this).apply {
            text = "⏰"
            textSize = 48f
            gravity = Gravity.CENTER
        }
        root.addView(icon)

        val spacer1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        }
        root.addView(spacer1)

        val titleView = TextView(this).apply {
            text = title
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(titleView)

        val spacer2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 16
            )
        }
        root.addView(spacer2)

        val messageView = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.argb(200, 255, 255, 255))
            gravity = Gravity.CENTER
        }
        root.addView(messageView)

        val spacer3 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 40
            )
        }
        root.addView(spacer3)

        val dismissBtn = Button(this).apply {
            text = "好，我去休息"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 76, 175, 80))
            setOnClickListener {
                Log.d(TAG, "用户点击休息按钮")
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifyId)
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }
        }
        root.addView(dismissBtn)

        setContentView(root)
        Log.d(TAG, "Activity 界面已显示")
    }

    override fun onBackPressed() {
        // 阻止返回键
    }

    override fun onDestroy() {
        super.onDestroy()
        val notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, 2003)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifyId)
    }
}
