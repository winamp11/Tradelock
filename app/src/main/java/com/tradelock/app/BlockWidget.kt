package com.tradelock.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

/** Home-screen widget: one tap locks MT5 until 10:00 AM UAE. */
class BlockWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_BLOCK = "com.tradelock.app.BLOCK_NOW"

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BlockWidget::class.java))
            if (ids.isEmpty()) return

            val st = LockPolicy.state(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_block)

            val tap: PendingIntent
            if (st.locked) {
                v.setTextViewText(R.id.widget_title, "🔒 Locked")
                v.setTextViewText(R.id.widget_sub, "Until ${Fmt.uaeTimeOnly(st.unlockAt)} UAE")
                v.setTextViewText(R.id.widget_button, "LOCKED")
                v.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.widget_btn_grey)
                tap = PendingIntent.getActivity(
                    ctx, 10, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                v.setTextViewText(R.id.widget_title, "Target hit?")
                v.setTextViewText(R.id.widget_sub, "Lock MT5 until 10 AM UAE")
                v.setTextViewText(R.id.widget_button, "BLOCK NOW")
                v.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.widget_btn_red)
                tap = PendingIntent.getBroadcast(
                    ctx, 11,
                    Intent(ctx, BlockWidget::class.java).setAction(ACTION_BLOCK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            v.setOnClickPendingIntent(R.id.widget_root, tap)
            v.setOnClickPendingIntent(R.id.widget_button, tap)
            mgr.updateAppWidget(ids, v)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_BLOCK) {
            val st = LockPolicy.blockNow(ctx)
            updateAll(ctx)
            Toast.makeText(
                ctx, "Locked until ${Fmt.uaeTimeOnly(st.unlockAt)} UAE. Profit banked. Well done.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
