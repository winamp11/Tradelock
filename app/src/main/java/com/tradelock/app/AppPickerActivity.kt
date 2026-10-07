package com.tradelock.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Pick an installed app to add to the block list. Adding never needs the password. */
class AppPickerActivity : Activity() {

    private data class AppRow(val pkg: String, val label: String, val icon: Drawable)

    private var rows: List<AppRow> = emptyList()
    private lateinit var list: ListView
    private lateinit var loading: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.styleWindow(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0)
        }
        root.addView(Ui.text(this, "Add an app to block", 22f, Ui.TEXT, bold = true))
        loading = Ui.text(this, "Loading apps…", 15f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 16), 0, 0)
        }
        root.addView(loading)
        list = ListView(this).apply {
            divider = null
            setOnItemClickListener { _, _, pos, _ -> add(rows[pos]) }
        }
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        Thread {
            val pm = packageManager
            val blocked = Prefs(this).blockedPackages
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName to it }
                .distinctBy { it.first }
                .filter { (pkg, _) -> pkg != packageName && pkg !in blocked }
                .map { (pkg, ri: ResolveInfo) ->
                    AppRow(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm))
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                rows = apps
                loading.visibility = View.GONE
                list.adapter = Adapter()
            }
        }.start()
    }

    private fun add(row: AppRow) {
        val p = Prefs(this)
        p.blockedPackages = p.blockedPackages + row.pkg
        Toast.makeText(this, "${row.label} added", Toast.LENGTH_SHORT).show()
        finish()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val ctx = this@AppPickerActivity
            val row = rows[position]
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10))
                addView(ImageView(ctx).apply { setImageDrawable(row.icon) },
                    LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 40)))
                addView(TextView(ctx).apply {
                    text = row.label
                    textSize = 16f
                    setTextColor(Ui.TEXT)
                    setPadding(Ui.dp(ctx, 14), 0, 0, 0)
                })
            }
        }
    }
}
