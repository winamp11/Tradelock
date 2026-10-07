package com.tradelock.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny view helpers so the UI needs no extra libraries. */
object Ui {
    val BG = Color.parseColor("#0F1115")
    val CARD = Color.parseColor("#1A1D24")
    val LINE = Color.parseColor("#2E323B")
    val TEXT = Color.parseColor("#EDEDED")
    val MUTED = Color.parseColor("#9BA1A6")
    val RED = Color.parseColor("#E5484D")
    val GREEN = Color.parseColor("#30A46C")
    val AMBER = Color.parseColor("#F5A524")

    fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun styleWindow(a: Activity) {
        a.window.statusBarColor = BG
        a.window.navigationBarColor = BG
    }

    fun rounded(color: Int, radiusDp: Int, ctx: Context) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp).toFloat()
    }

    fun text(ctx: Context, s: String, sizeSp: Float, color: Int, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun button(ctx: Context, label: String, color: Int, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(color, 14, ctx)
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            setOnClickListener { onClick() }
        }

    /** Small pill-style button for list rows. */
    fun chip(ctx: Context, label: String, color: Int, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(color, 10, ctx)
            setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
            setOnClickListener { onClick() }
        }

    fun card(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 18, ctx)
        setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(ctx, 14) }
    }

    fun passwordField(ctx: Context, hint: String) = EditText(ctx).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setTextColor(TEXT)
        setHintTextColor(MUTED)
        setSingleLine()
    }
}
