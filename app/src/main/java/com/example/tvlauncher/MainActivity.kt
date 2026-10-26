package com.example.tvlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

private val BONE = 0xFFD9CFC1.toInt()
private val DIM = 0xFF8A7D72.toInt()
private val BLOOD = 0xFFB3121A.toInt()
private val TILE = 0xCC120C0D.toInt()
private val TILE_FOCUS = 0xFF2A0C0F.toInt()
private val EDGE = 0xFF2B1A1B.toInt()
private val LINE = 0x66B3121A.toInt()

private class BrandView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BLOOD
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val r = RectF()
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.strokeWidth = w * 0.07f
        r.set(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f)
        cv.drawArc(r, 120f, 300f, false, p)
        cv.drawLine(w * 0.5f, h * 0.02f, w * 0.5f, h * 0.98f, p)
        cv.drawLine(w * 0.30f, h * 0.36f, w * 0.70f, h * 0.36f, p)
    }
}

class MainActivity : Activity() {

    data class App(val pkg: String, val label: String)
    class VH(v: View, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(v)

    private lateinit var rv: RecyclerView
    private val adapter = AppAdapter()
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val reload = Runnable { load() }
    private val icons = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            handler.removeCallbacks(reload)
            handler.postDelayed(reload, 600)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun txt(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.SERIF
        letterSpacing = 0.08f
    }

    private fun clock(fmt: String, size: Float, color: Int) = TextClock(this).apply {
        format12Hour = fmt
        format24Hour = fmt
        textSize = size
        setTextColor(color)
        typeface = Typeface.SERIF
        letterSpacing = 0.06f
        setAllCaps(true)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setBackgroundDrawable(null)
        val cols = (resources.displayMetrics.widthPixels / dp(200)).coerceIn(4, 8)

        val bg = GradientDrawable()
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        bg.setGradientCenter(0.5f, 1.15f)
        bg.setGradientRadius(dp(1500).toFloat())
        bg.setColors(intArrayOf(0xFF3A0A0E.toInt(), 0xFF140A0C.toInt(), 0xFF050304.toInt()))

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, 0, 0)
        }
        titles.addView(txt("БЕРСЕРК", 30f, BONE, true))
        titles.addView(clock("EEEE, d MMMM", 14f, DIM))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(BrandView(this), LinearLayout.LayoutParams(dp(64), dp(64)))
        header.addView(titles, LinearLayout.LayoutParams(-2, -2))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(clock("HH:mm", 36f, BONE), LinearLayout.LayoutParams(-2, -2))

        val line = View(this).apply { setBackgroundColor(LINE) }

        rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, cols)
            setHasFixedSize(true)
            itemAnimator = null
            setItemViewCacheSize(cols * 3)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = this@MainActivity.adapter
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(40), dp(56), dp(24))
        }
        content.addView(header, LinearLayout.LayoutParams(-1, -2))
        content.addView(line, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(16)
            bottomMargin = dp(8)
        })
        content.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))

        val root = FrameLayout(this)
        root.background = bg
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, f)
        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        rv.scrollToPosition(0)
        rv.post { rv.getChildAt(0)?.requestFocus() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(reload)
        unregisterReceiver(receiver)
        io.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { /* лаунчер не закрывается кнопкой Назад */ }

    private fun load() {
        io.execute {
            val pm = packageManager
            val list = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { adapter.submit(list) }
        }
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
    }

    private fun iconBitmap(pkg: String): Bitmap? = try {
        val d = packageManager.getApplicationIcon(pkg)
        val s = dp(96)
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, s, s)
        d.draw(Canvas(b))
        b
    } catch (e: Exception) {
        null
    }

    inner class AppAdapter : RecyclerView.Adapter<VH>() {
        private var items = listOf<App>()

        fun submit(l: List<App>) {
            val first = items.isEmpty()
            items = l
            notifyDataSetChanged()
            if (first) rv.post { rv.getChildAt(0)?.requestFocus() }
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val normal = GradientDrawable().apply {
                setColor(TILE)
                setStroke(dp(1), EDGE)
                cornerRadius = dp(2).toFloat()
            }
            val focused = GradientDrawable().apply {
                setColor(TILE_FOCUS)
                setStroke(dp(3), BLOOD)
                cornerRadius = dp(2).toFloat()
            }
            val states = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
            val col = LinearLayout(p.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                background = states
                setPadding(dp(8), dp(18), dp(8), dp(14))
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                    setMargins(dp(8), dp(8), dp(8), dp(8))
                }
            }
            val icon = ImageView(p.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = 0.72f
            }
            val label = txt("", 15f, DIM, false).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setAllCaps(true)
                setPadding(0, dp(10), 0, 0)
            }
            col.addView(icon)
            col.addView(label)
            col.setOnFocusChangeListener { v, f ->
                val s = if (f) 1.08f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(90).start()
                icon.alpha = if (f) 1f else 0.72f
                label.setTextColor(if (f) BONE else DIM)
            }
            return VH(col, icon, label)
        }

        override fun onBindViewHolder(h: VH, i: Int) {
            val a = items[i]
            h.label.text = a.label
            h.itemView.setOnClickListener { launch(a.pkg) }
            h.icon.tag = a.pkg
            val c = icons.get(a.pkg)
            if (c != null) {
                h.icon.setImageBitmap(c)
            } else {
                h.icon.setImageBitmap(null)
                io.execute {
                    val b = iconBitmap(a.pkg)
                    if (b != null) {
                        icons.put(a.pkg, b)
                        runOnUiThread { if (h.icon.tag == a.pkg) h.icon.setImageBitmap(b) }
                    }
                }
            }
        }
    }
}
