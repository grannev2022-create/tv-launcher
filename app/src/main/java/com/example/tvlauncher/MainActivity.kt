package com.example.tvlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    data class App(val pkg: String, val label: String)
    class VH(v: View, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(v)

    private lateinit var rv: RecyclerView
    private val adapter = AppAdapter()
    private val io = Executors.newSingleThreadExecutor()
    private val cache = LruCache<String, Drawable>(80)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { load() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        rv = RecyclerView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 24))
            layoutManager = GridLayoutManager(context, 6)
            setHasFixedSize(true)
            clipToPadding = false
            setPadding(dp(48), dp(48), dp(48), dp(48))
            adapter = this@MainActivity.adapter
        }
        setContentView(rv)
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, f)
        load()
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
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
            val col = LinearLayout(p.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                setPadding(dp(8), dp(16), dp(8), dp(16))
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
            val icon = ImageView(p.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
            }
            val label = TextView(p.context).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            col.addView(icon)
            col.addView(label)
            col.setOnFocusChangeListener { v, f ->
                val s = if (f) 1.12f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
                v.setBackgroundColor(if (f) Color.argb(60, 255, 255, 255) else Color.TRANSPARENT)
            }
            return VH(col, icon, label)
        }

        override fun onBindViewHolder(h: VH, i: Int) {
            val a = items[i]
            h.label.text = a.label
            h.itemView.setOnClickListener { launch(a.pkg) }
            h.icon.tag = a.pkg
            val c = cache.get(a.pkg)
            if (c != null) {
                h.icon.setImageDrawable(c)
            } else {
                h.icon.setImageDrawable(null)
                io.execute {
                    val d = try { packageManager.getApplicationIcon(a.pkg) } catch (e: Exception) { null }
                    if (d != null) {
                        cache.put(a.pkg, d)
                        runOnUiThread { if (h.icon.tag == a.pkg) h.icon.setImageDrawable(d) }
                    }
                }
            }
        }
    }
}
