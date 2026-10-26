package io.github.maxhayim.tvhub.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.maxhayim.tvhub.R
import io.github.maxhayim.tvhub.TvHubApp
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.player.PlayerActivity
import io.github.maxhayim.tvhub.ui.Ui.dp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The hub's window: a tab bar along the top and one page under it. Moving focus along the tabs
 * switches the page; Down enters it; Back returns to the tabs, and Back on the tabs leaves.
 */
class MainActivity : Activity() {
    val scope = MainScope()
    val repo get() = (application as TvHubApp).repo

    enum class Page(val label: String) { HOME("Home"), LIVE("Live TV"), GUIDE("Guide"), FAVORITES("Favorites"), SOURCES("Sources"), SETTINGS("Settings") }

    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private val navItems = LinkedHashMap<Page, TextView>()
    private val screens = HashMap<Page, Screen>()
    private var current: Page? = null
    /**
     * Between leaving this window and getting it back, Android may hand focus to the first rail
     * item (when the view that had it was rebuilt meanwhile). That must not switch the page.
     */
    private var restoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.vertical(this).apply { background = backdrop() }

        // The top bar: the name, one tab per page, then the refresh status and a clock on the right.
        val bar = Ui.horizontal(this, 6).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(40), dp(22), dp(40), dp(10))
        }
        bar.addView(Ui.text(this, getString(R.string.app_name), 24f, bold = true).apply { setPadding(0, 0, dp(26), 0) })
        Page.entries.forEach { p ->
            val item = Ui.text(this, p.label, 17f, Ui.DIM, bold = true).apply {
                isFocusable = true; isClickable = true
                background = Ui.focusable(this@MainActivity, 0x00000000, 22, 0x33E11D2E)
                setPadding(dp(18), dp(9), dp(18), dp(9))
                setOnFocusChangeListener { _, has -> if (has && !restoring) show(p); refreshNavColors() }
                setOnClickListener { focusContent() }
                // Down always enters the page at its first control, not whatever sits nearest.
                setOnKeyListener { _, code, e -> code == KeyEvent.KEYCODE_DPAD_DOWN && e.action == KeyEvent.ACTION_DOWN && focusContent() }
                id = View.generateViewId()
            }
            navItems[p] = item
            bar.addView(item)
        }
        // The tabs end at both ends instead of wandering off the bar.
        navItems.values.first().let { it.nextFocusLeftId = it.id }
        navItems.values.last().let { it.nextFocusRightId = it.id }
        bar.addView(View(this), Ui.lp(0, 1, 1f))
        status = Ui.text(this, "", 13f, Ui.DIM, lines = 1).apply { setPadding(0, 0, dp(18), 0) }
        bar.addView(status)
        bar.addView(android.widget.TextClock(this).apply {
            typeface = Ui.face(this@MainActivity); textSize = 20f; setTextColor(Ui.TEXT)
        })
        root.addView(bar, Ui.lp(Ui.MATCH, Ui.WRAP))
        // A hairline under the bar, bright in the middle and fading out at the ends.
        root.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF))
        }, Ui.lp(Ui.MATCH, dp(1)).apply { setMargins(dp(40), 0, dp(40), 0) })

        content = FrameLayout(this).apply { setPadding(dp(32), dp(18), dp(32), dp(16)); clipChildren = false; clipToPadding = false }
        root.addView(content, Ui.lp(Ui.MATCH, 0, 1f))
        setContentView(root)

        scope.launch {
            repo.refresh.collect { st ->
                status.text = st.message
                status.setTextColor(if (!st.running && st.errors > 0) Ui.ERROR else Ui.DIM)
            }
        }

        val start = intent.getStringExtra(EXTRA_PAGE)?.let { runCatching { Page.valueOf(it) }.getOrNull() } ?: Page.HOME
        navItems.getValue(start).requestFocus()
        show(start)

        if (!repo.prefs.onboarded) startActivity(Intent(this, OnboardingActivity::class.java))
        else repo.refreshIfStale()
    }

    override fun onResume() {
        super.onResume()
        current?.let { screens[it]?.onShow() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !restoring) return
        content.post {
            restoring = false
            val f = currentFocus
            val cur = current ?: return@post
            if (f == null || (navItems.values.any { it === f } && f !== navItems[cur])) navItems.getValue(cur).requestFocus()
        }
    }

    override fun onPause() {
        restoring = true
        current?.let { screens[it]?.onHide() }
        super.onPause()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    fun show(p: Page) {
        if (current == p) return
        current?.let { screens[it]?.onHide() }
        current = p
        val s = screens.getOrPut(p) {
            when (p) {
                Page.HOME -> HomeScreen(this)
                Page.LIVE -> ChannelsScreen(this, favoritesOnly = false)
                Page.GUIDE -> GuideScreen(this)
                Page.FAVORITES -> ChannelsScreen(this, favoritesOnly = true)
                Page.SOURCES -> SourcesScreen(this)
                Page.SETTINGS -> SettingsScreen(this)
            }
        }
        content.removeAllViews()
        // A margin inside the page where focused controls can grow (scaleOnFocus) without being cut.
        s.view.setPadding(dp(8), s.view.paddingTop, dp(8), s.view.paddingBottom)
        (s.view as? android.view.ViewGroup)?.clipToPadding = false
        content.addView(s.view, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        // The tab above the page is the current one, so Up from the page's top row returns to it.
        s.view.nextFocusUpId = navItems.getValue(p).id
        s.onShow()
        refreshNavColors()
    }

    private fun refreshNavColors() {
        navItems.forEach { (p, v) ->
            v.isSelected = p == current && !v.isFocused
            v.setTextColor(if (p == current || v.isFocused) Ui.TEXT else Ui.DIM)
        }
    }

    fun focusContent(): Boolean = current?.let { screens[it]?.focusFirst() } ?: false

    fun go(p: Page) { navItems.getValue(p).requestFocus(); show(p); content.post { focusContent() } }

    fun play(list: List<Channel>, index: Int) {
        if (list.isEmpty()) return
        PlayerActivity.start(this, list, index)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val focus = currentFocus
            if (focus != null && navItems.values.none { it === focus }) {
                current?.let { navItems.getValue(it).requestFocus() }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Near-black with a faint crimson glow at the top right, as on the launcher. */
    private fun backdrop() = android.graphics.drawable.GradientDrawable().apply {
        gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(0x55B3001B, Ui.BG)
        setGradientCenter(0.9f, 0f)
        gradientRadius = resources.displayMetrics.widthPixels * 0.7f
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }
}

/** Gravity helper for one-off centred text. */
internal fun TextView.centered(): TextView = apply { gravity = Gravity.CENTER }
