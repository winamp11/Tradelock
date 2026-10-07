package com.tradelock.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var statusTitle: TextView? = null
    private var statusSub: TextView? = null
    private var statusLocal: TextView? = null
    private var shownLocked: Boolean? = null

    private val tick = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.styleWindow(this)
    }

    override fun onResume() {
        super.onResume()
        render()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun render() {
        if (!Password.isSet(this)) showSetup() else showHome()
    }

    // ------------------------------------------------------------------ setup

    private fun showSetup() {
        statusTitle = null
        val col = column()
        col.addView(Ui.text(this, "Partner setup", 28f, Ui.TEXT, bold = true))
        col.addView(Ui.text(this,
            "Hand the phone to your wife. She sets the password and keeps it to herself.\n\n" +
                "You never need it to lock. It's only needed to unlock early, remove an app " +
                "from the block list, or change the password.", 16f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 8))
        })
        val card = Ui.card(this)
        val pw1 = Ui.passwordField(this, "Password (min ${Password.MIN_LENGTH} characters)")
        val pw2 = Ui.passwordField(this, "Repeat password")
        card.addView(pw1)
        card.addView(pw2)
        col.addView(card)
        col.addView(spacer(18))
        col.addView(Ui.button(this, "Set password", Ui.RED) {
            val a = pw1.text.toString()
            when {
                a.length < Password.MIN_LENGTH -> pw1.error = "At least ${Password.MIN_LENGTH} characters"
                a != pw2.text.toString() -> pw2.error = "Passwords don't match"
                else -> {
                    Password.set(this, a)
                    Toast.makeText(this, "Password set. Hand the phone back.", Toast.LENGTH_LONG).show()
                    render()
                }
            }
        })
        setScreen(col)
    }

    // ------------------------------------------------------------------- home

    private fun showHome() {
        val st = LockPolicy.state(this)
        shownLocked = st.locked
        val col = column()
        col.addView(Ui.text(this, "TradeLock", 28f, Ui.TEXT, bold = true))
        col.addView(Ui.text(this, "Trading window 10:00 AM – midnight UAE", 14f, Ui.MUTED))

        // Status
        val status = Ui.card(this)
        statusTitle = Ui.text(this, "", 22f, Ui.TEXT, bold = true)
        statusSub = Ui.text(this, "", 15f, Ui.TEXT)
        statusLocal = Ui.text(this, "", 13f, Ui.MUTED)
        status.addView(statusTitle)
        status.addView(statusSub)
        status.addView(statusLocal)
        col.addView(status)
        col.addView(spacer(14))

        if (st.locked) {
            col.addView(Ui.button(this, "Unlock with partner password", Ui.LINE) {
                askPassword("Unlock early", "Partner password") {
                    LockPolicy.passwordUnlock(this)
                    BlockWidget.updateAll(this)
                    Toast.makeText(this, "Unlocked until the next lock.", Toast.LENGTH_SHORT).show()
                    render()
                }
            })
        } else {
            col.addView(Ui.button(this, "BLOCK NOW · lock until 10 AM UAE", Ui.RED) {
                val s = LockPolicy.blockNow(this)
                BlockWidget.updateAll(this)
                Toast.makeText(this, "Locked until ${Fmt.uaeTimeOnly(s.unlockAt)} UAE. Well traded.",
                    Toast.LENGTH_LONG).show()
                render()
            })
        }

        // Protection checklist
        val prot = Ui.card(this)
        prot.addView(sectionTitle("Protection"))
        prot.addView(checkRow("App blocker", "Required. Closes blocked apps.",
            accessibilityOn(), "Turn on") {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Turn on the blocker")
                .setMessage("On the next screen, open \"Installed apps\" (or \"Downloaded apps\"), tap TradeLock and switch it on.\n\n" +
                    "If the switch is greyed out: go to Settings › Apps › TradeLock › ⋮ menu (top right) › " +
                    "\"Allow restricted settings\", then try again.")
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        if (Build.VERSION.SDK_INT >= 33) {
            prot.addView(checkRow("11:45 PM warning", "Notification before the midnight lock.",
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                "Allow") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            })
        }
        prot.addView(checkRow("Uninstall protection", "Recommended. Stops uninstalling TradeLock.",
            adminOn(), "Turn on") {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stops TradeLock from being uninstalled or reset in a weak moment. It uses no other powers."))
        })
        col.addView(prot)

        // Blocked apps
        val apps = Ui.card(this)
        apps.addView(sectionTitle("Blocked apps"))
        val blocked = Prefs(this).blockedPackages.sortedBy { labelFor(it).lowercase() }
        if (blocked.isEmpty()) apps.addView(Ui.text(this, "No apps yet.", 14f, Ui.MUTED))
        for (pkg in blocked) {
            apps.addView(row(labelFor(pkg), pkg, Ui.chip(this, "Remove", Ui.LINE) {
                askPassword("Remove ${labelFor(pkg)}?", "Partner password") {
                    val p = Prefs(this)
                    p.blockedPackages = p.blockedPackages - pkg
                    render()
                }
            }))
        }
        apps.addView(spacer(10))
        apps.addView(Ui.button(this, "+ Add app", Ui.LINE) {
            startActivity(Intent(this, AppPickerActivity::class.java))
        })
        col.addView(apps)

        // Settings
        val settings = Ui.card(this)
        settings.addView(sectionTitle("Partner"))
        settings.addView(Ui.button(this, "Change password", Ui.LINE) { changePassword() })
        col.addView(settings)

        val count = Prefs(this).lockCount
        if (count > 0) {
            col.addView(Ui.text(this, "You've walked away at target $count time${if (count == 1) "" else "s"}.",
                14f, Ui.GREEN).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(context, 20), 0, 0)
            })
        }
        col.addView(Ui.text(this, "Tip: long-press your home screen › Widgets › TradeLock to add the Block button.",
            13f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 16), 0, Ui.dp(context, 32))
        })
        setScreen(col)
        refreshStatus()
    }

    private fun refreshStatus() {
        val title = statusTitle ?: return
        val st = LockPolicy.state(this)
        if (st.locked != shownLocked) { render(); return }

        if (st.locked) {
            title.text = "🔒 Locked · ${Fmt.countdown(st.unlockAt - st.now)}"
            title.setTextColor(Ui.RED)
            statusSub?.text = (if (st.reason == LockPolicy.Reason.CURFEW) "Midnight lock. " else "") +
                "Unlocks ${Fmt.uae(st.unlockAt)}"
            statusLocal?.text = Fmt.localIfDifferent(st.unlockAt) ?: ""
        } else {
            val midnight = LockPolicy.uae(st.now).toLocalDate().plusDays(1)
                .atStartOfDay(LockPolicy.UAE).toInstant().toEpochMilli()
            title.text = "Open"
            title.setTextColor(Ui.GREEN)
            statusSub?.text = "Auto-lock at midnight UAE in ${Fmt.countdown(midnight - st.now)}"
            statusLocal?.text = Fmt.localIfDifferent(midnight) ?: ""
        }
        if (!accessibilityOn()) {
            statusLocal?.text = "⚠ Blocker is OFF. Turn it on below or nothing gets blocked."
            statusLocal?.setTextColor(Ui.AMBER)
        } else {
            statusLocal?.setTextColor(Ui.MUTED)
        }
    }

    // --------------------------------------------------------------- password

    private fun askPassword(title: String, hint: String, onOk: () -> Unit) {
        val field = Ui.passwordField(this, hint)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setView(padded(field))
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when (val r = Password.check(this, field.text.toString())) {
                    is Password.Result.Ok -> { dialog.dismiss(); onOk() }
                    is Password.Result.Wrong -> {
                        field.text.clear()
                        field.error = "Wrong password. ${r.attemptsLeft} tries left."
                    }
                    is Password.Result.TooManyAttempts -> {
                        field.text.clear()
                        field.error = "Too many tries. Wait until ${Fmt.uaeTimeOnly(r.retryAt)} UAE."
                    }
                }
            }
        }
        dialog.show()
    }

    private fun changePassword() {
        askPassword("Change password", "Current password") {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val a = Ui.passwordField(this, "New password")
            val b = Ui.passwordField(this, "Repeat new password")
            box.addView(a)
            box.addView(b)
            val d = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("New password")
                .setView(padded(box))
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create()
            d.setOnShowListener {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val p = a.text.toString()
                    when {
                        p.length < Password.MIN_LENGTH -> a.error = "At least ${Password.MIN_LENGTH} characters"
                        p != b.text.toString() -> b.error = "Passwords don't match"
                        else -> {
                            Password.set(this, p)
                            d.dismiss()
                            Toast.makeText(this, "Password changed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            d.show()
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun accessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun adminComponent() = ComponentName(this, AdminReceiver::class.java)

    private fun adminOn(): Boolean =
        getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent())

    private fun labelFor(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        if (pkg == Prefs.MT5) "MetaTrader 5" else pkg
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(context, 18), Ui.dp(context, 28), Ui.dp(context, 18), Ui.dp(context, 12))
    }

    private fun setScreen(content: View) {
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(content)
        })
    }

    private fun spacer(dp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, Ui.dp(context, dp))
    }

    private fun sectionTitle(s: String) = Ui.text(this, s.uppercase(), 12f, Ui.MUTED, bold = true).apply {
        setPadding(0, 0, 0, Ui.dp(context, 8))
        letterSpacing = 0.08f
    }

    private fun row(title: String, sub: String, action: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8))
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(context, title, 16f, Ui.TEXT))
            addView(Ui.text(context, sub, 12f, Ui.MUTED))
        }
        addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(action)
    }

    private fun checkRow(title: String, sub: String, ok: Boolean, actionLabel: String, onFix: () -> Unit): View =
        row((if (ok) "✅ " else "⚠️ ") + title, sub,
            if (ok) Ui.text(this, "On", 14f, Ui.GREEN, bold = true)
            else Ui.chip(this, actionLabel, Ui.RED, onFix))

    private fun padded(v: View) = FrameLayout(this).apply {
        setPadding(Ui.dp(context, 22), Ui.dp(context, 8), Ui.dp(context, 22), 0)
        addView(v)
    }
}
