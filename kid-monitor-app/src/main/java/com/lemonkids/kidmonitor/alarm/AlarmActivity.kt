package com.lemonkids.kidmonitor.alarm

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemonkids.kidmonitor.ui.theme.KidMonitorTheme

/** 锁屏全屏提醒。只在用户操作关闭后停止音频，防止误触通知即静音。 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val alarmId = intent.getStringExtra(AlarmRingService.EXTRA_ALARM_ID).orEmpty()
        val revision = intent.getLongExtra(AlarmRingService.EXTRA_REVISION, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "闹钟时间到了"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "请完成家长设置的提醒"
        setContent {
            KidMonitorTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF173B22))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🍋", fontSize = 88.sp)
                    Spacer(Modifier.height(20.dp))
                    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(message, fontSize = 18.sp, color = Color.White.copy(alpha = 0.86f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(42.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD84A), contentColor = Color(0xFF173B22)),
                        onClick = {
                            startService(AlarmRingService.stopIntent(this@AlarmActivity, alarmId, revision))
                            finish()
                        }
                    ) { Text("我知道了，关闭闹钟", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
    }
}
