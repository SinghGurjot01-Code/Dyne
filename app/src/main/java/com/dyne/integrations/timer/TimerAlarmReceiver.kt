package com.dyne.integrations.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dyne.dyne

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.dyne.timer.onAlarm()
    }
}
