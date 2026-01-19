package com.my.mg.receiver

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.my.mg.MGWidget
//无意义，andeoid禁止亮屏信号静态注册，只能写常驻服务检测。
class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
//        if (Intent.ACTION_SCREEN_ON == intent.action) {
//            val appWidgetManager = AppWidgetManager.getInstance(context)
//            val componentName = ComponentName(context, MGWidget::class.java)
//            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
//
//            for (id in appWidgetIds) {
//                MGWidget.updateAppWidget(context, appWidgetManager, id)
//            }
//        }
    }
}
