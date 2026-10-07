package com.tradelock.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** The screen you see instead of MT5 while locked. */
class BlockedActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countdown: TextView
    private lateinit var until: TextView
    private lateinit var local: TextView

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.styleWindow(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(this@BlockedActivity, 32), 0, Ui.dp(this@BlockedActivity, 32), 0)
        }
        root.addView(Ui.text(this, "🔒", 64f, Ui.TEXT).apply { gravity = Gravity.CENTER })
        root.addView(Ui.text(this, "Trading is closed", 28f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
        })
        root.addView(Ui.text(this, "You banked the profit. The market will still be there tomorrow.", 16f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 28))
        })
        countdown = Ui.text(this, "", 44f, Ui.RED, bold = true).apply { gravity = Gravity.CENTER }
        until = Ui.text(this, "", 16f, Ui.TEXT).apply { gravity = Gravity.CENTER }
        local = Ui.text(this, "", 14f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(countdown)
        root.addView(until)
        root.addView(local)
        root.addView(Ui.button(this, "Close", Ui.CARD) { goHome() }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(context, 40)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh() {
        val st = LockPolicy.state(this)
        if (!st.locked) { finish(); return }
        countdown.text = Fmt.countdown(st.unlockAt - st.now)
        until.text = "Unlocks ${Fmt.uae(st.unlockAt)}"
        local.text = Fmt.localIfDifferent(st.unlockAt) ?: ""
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goHome()
}
