package com.lemonkids.kidmonitor.alarm

import android.os.Build
import android.os.Bundle
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

/** 锁屏全屏提醒；与覆盖层共用同一展示模型、配色和单次关闭规则。 */
class AlarmActivity : ComponentActivity() {
    private lateinit var presentation: AlarmPresentation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        presentation = AlarmPresentation(
            alarmId = intent.getStringExtra(AlarmRingService.EXTRA_ALARM_ID).orEmpty(),
            revision = intent.getLongExtra(AlarmRingService.EXTRA_REVISION, -1L),
            title = intent.getStringExtra(EXTRA_TITLE) ?: AlarmPresentation.DEFAULT_TITLE,
            message = intent.getStringExtra(EXTRA_MESSAGE) ?: AlarmPresentation.DEFAULT_MESSAGE,
            requiresConfirmation = intent.getBooleanExtra(EXTRA_REQUIRES_CONFIRMATION, true)
        )
        setContent {
            KidMonitorTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFFFEEF5)).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(AlarmPresentationUi.ICON, fontSize = 88.sp)
                    Spacer(Modifier.height(20.dp))
                    Text(presentation.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B2347), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(presentation.message, fontSize = 18.sp, color = Color(0xFF6A3853), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(42.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF75A8), contentColor = Color.White),
                        onClick = {
                            startService(AlarmRingService.stopIntent(this@AlarmActivity, presentation.alarmId, presentation.revision))
                            finish()
                        }
                    ) { Text(AlarmPresentationUi.DISMISS_LABEL, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmPresentationRegistry.activityVisible(presentation.session)
    }

    override fun onPause() {
        AlarmPresentationRegistry.activityHidden(presentation.session)
        super.onPause()
    }

    override fun onDestroy() {
        AlarmPresentationRegistry.activityHidden(presentation.session)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_REQUIRES_CONFIRMATION = "requires_confirmation"
    }
}
