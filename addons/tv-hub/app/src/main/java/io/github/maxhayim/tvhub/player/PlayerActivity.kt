package io.github.maxhayim.tvhub.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.maxhayim.tvhub.TvHubApp
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.model.Programme
import io.github.maxhayim.tvhub.model.StreamRef
import io.github.maxhayim.tvhub.ui.Ui
import io.github.maxhayim.tvhub.ui.Ui.dp
import io.github.maxhayim.tvhub.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Full-screen live playback with Media3 (HLS, DASH without DRM, and progressive streams). Keys:
 * Up/Down and Channel +/- change channel; Left opens the channel list over the picture; the Last
 * button goes back to the previous channel; digits tune by number; OK or Right shows the channel
 * banner; Menu or a long OK opens the player menu (audio, subtitles, quality, picture, statistics,
 * favourite). Back closes a panel, then returns to where playback started. The player is released as
 * soon as the screen stops.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private val scope = MainScope()
    private val app get() = application as TvHubApp
    private val repo get() = app.repo
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("player", MODE_PRIVATE) }

    private var player: ExoPlayer? = null
    private var selector: DefaultTrackSelector? = null
    private lateinit var playerView: PlayerView
    private lateinit var spinner: ProgressBar
    private lateinit var overlay: InfoOverlay
    private lateinit var stats: TextView
    private lateinit var panel: ChannelPanel
    private lateinit var errorBox: LinearLayout
    private lateinit var errorText: TextView

    private var list: List<Channel> = emptyList()
    private var index = 0
    private var previous = -1
    private var streamIndex = 0
    private var retries = 0
    private var triedHls = false
    private var digits = ""

    /** Video height cap: 0 for no cap. 720 by default, the panel's own resolution. */
    private var maxHeight: Int
        get() = prefs.getInt("max_height", 720)
        set(v) { prefs.edit().putInt("max_height", v).apply() }
    private var resize: Int
        get() = prefs.getInt("resize", AspectRatioFrameLayout.RESIZE_MODE_FIT)
        set(v) { prefs.edit().putInt("resize", v).apply() }
    private var showStats: Boolean
        get() = prefs.getBoolean("stats", false)
        set(v) { prefs.edit().putBoolean("stats", v).apply() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        list = Queue.channels
        index = Queue.index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        if (list.isEmpty()) { finish(); return }

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = resize
            setShutterBackgroundColor(0xFF000000.toInt())
            isFocusable = false
        }
        root.addView(playerView, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        spinner = ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT) }
        root.addView(spinner, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        overlay = InfoOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM))
        stats = Ui.text(this, "", 13f, Ui.TEXT, lines = 0).apply {
            background = glass(14)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            typeface = android.graphics.Typeface.MONOSPACE
            visibility = if (showStats) View.VISIBLE else View.GONE
        }
        root.addView(stats, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(32), dp(40), 0) })
        panel = ChannelPanel(this)
        root.addView(panel, FrameLayout.LayoutParams(dp(440), Ui.MATCH, Gravity.START))
        errorBox = Ui.vertical(this, 10).apply {
            background = glass(18)
            setPadding(dp(32), dp(24), dp(32), dp(24)); visibility = View.GONE
        }
        errorText = Ui.text(this, "", 18f, lines = 4)
        errorBox.addView(errorText)
        errorBox.addView(Ui.text(this, "OK: try again · Up/Down: other channels · Left: channel list · Back: guide", 15f, Ui.DIM, lines = 2))
        root.addView(errorBox, FrameLayout.LayoutParams(dp(680), Ui.WRAP, Gravity.CENTER))
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        if (list.isEmpty()) return
        createPlayer()
        tune(index, remember = false)
        main.post(statsTick)
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() { scope.cancel(); main.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun glass(radiusDp: Int) = Ui.rounded(this, 0xD9101014.toInt(), radiusDp, 0x40FFFFFF, 1)

    private fun createPlayer() {
        if (player != null) return
        val sel = DefaultTrackSelector(this).also { selector = it }
        applyQuality(sel)
        // Start fast, then keep a deeper buffer than the default so a shaky stream does not stall.
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_200, 2_500).build()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderers).setTrackSelector(sel).setLoadControl(load).build().also { p ->
            playerView.player = p
            p.playWhenReady = true
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    spinner.visibility = if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE && errorBox.visibility != View.VISIBLE) View.VISIBLE else View.GONE
                    if (state == Player.STATE_READY) {
                        retries = 0; errorBox.visibility = View.GONE
                        // One line per tune for diagnostics: formats only, never the address.
                        val v = p.videoFormat; val a = p.audioFormat
                        Log.i("Playing: video ${v?.sampleMimeType} ${v?.width}x${v?.height}, audio ${a?.sampleMimeType} ${a?.channelCount}ch")
                        overlay.bindTech()
                    }
                    // A live stream that ends was cut by the server; start it again.
                    if (state == Player.STATE_ENDED) recover("The stream ended")
                }
                override fun onPlayerError(error: PlaybackException) { onError(error) }
                override fun onTracksChanged(tracks: Tracks) { overlay.bindTech() }
            })
        }
    }

    private fun applyQuality(sel: DefaultTrackSelector) {
        val h = maxHeight
        sel.parameters = sel.buildUponParameters().apply {
            if (h > 0) setMaxVideoSize(Int.MAX_VALUE, h) else clearVideoSizeConstraints()
        }.build()
    }

    private fun releasePlayer() {
        main.removeCallbacksAndMessages(null)
        playerView.player = null
        player?.release()
        player = null
        selector = null
    }

    private fun tune(i: Int, remember: Boolean = true) {
        if (list.isEmpty()) return
        val target = ((i % list.size) + list.size) % list.size
        if (remember && target != index) previous = index
        index = target
        streamIndex = 0; retries = 0; triedHls = false
        errorBox.visibility = View.GONE
        val ch = list[index]
        repo.addRecent(ch.key)
        playStream(ch.streams.getOrNull(0))
        showInfo()
    }

    private fun playStream(s: StreamRef?, forceHls: Boolean = false) {
        val p = player ?: return
        if (s == null) { showError("This channel has no stream address."); return }
        val headers = HashMap<String, String>()
        s.referrer?.let { headers["Referer"] = it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(s.userAgent ?: "TVHub/1.0 (Android TV; Media3)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000).setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(headers)
        val item = MediaItem.Builder().setUri(s.url).apply {
            when {
                forceHls || looksHls(s.url) -> setMimeType(MimeTypes.APPLICATION_M3U8)
                looksDash(s.url) -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
            setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(C.TIME_UNSET).build())
        }.build()
        // One retry per load: Media3's default of three, each waiting on a timeout, kept a dead host
        // on a spinner for over a minute. Recovery below decides what happens next.
        val factory = DefaultMediaSourceFactory(this).setDataSourceFactory(http)
            .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1))
        p.setMediaSource(factory.createMediaSource(item))
        p.prepare()
        p.play()
    }

    private fun looksHls(url: String) = url.substringBefore('?').lowercase().let { it.endsWith(".m3u8") || it.endsWith(".m3u") } || url.contains("m3u8", ignoreCase = true)
    private fun looksDash(url: String) = url.substringBefore('?').lowercase().endsWith(".mpd") || url.contains("format=mpd", ignoreCase = true)

    /**
     * Recovery: fall behind a live window → jump to live; a stream with no extension that is really
     * HLS → retry as HLS; network trouble → retry twice with a short wait; then the next stream of
     * a merged channel; then say what went wrong.
     */
    private fun onError(e: PlaybackException) {
        Log.w("Playback error ${e.errorCodeName}")
        val p = player ?: return
        val ch = list[index]
        val s = ch.streams.getOrNull(streamIndex)
        when {
            e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { p.seekToDefaultPosition(); p.prepare() }
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !triedHls && s != null && !looksHls(s.url) && !looksDash(s.url) -> {
                triedHls = true; playStream(s, forceHls = true)
            }
            isTransient(e) && retries < 2 -> recover(null)
            streamIndex + 1 < ch.streams.size -> {
                streamIndex++; retries = 0; triedHls = false
                playStream(ch.streams[streamIndex])
                overlay.flash("Trying ${ch.streams[streamIndex].sourceName}")
            }
            else -> showError(message(e))
        }
    }

    private fun recover(note: String?) {
        retries++
        if (retries > 3) { showError(note ?: "The stream keeps stopping."); return }
        spinner.visibility = View.VISIBLE
        main.postDelayed({ player?.let { it.seekToDefaultPosition(); it.prepare(); it.play() } }, 1500L * retries)
    }

    /** Errors worth starting again from scratch. Connection failures are not: the load already retried. */
    private fun isTransient(e: PlaybackException) = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT -> true
        else -> false
    }

    private fun message(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (val code = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode) {
            404, 410 -> "The stream is not on its server any more (HTTP $code)."
            401, 403 -> "The stream's server refused it (HTTP $code). The link may have expired, or the account may be in use elsewhere."
            null -> "The stream's server answered with an error."
            else -> "The stream's server answered HTTP $code."
        }
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The stream was not found on its server."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Can't reach the stream's server."
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "This TV blocked an unencrypted (http) stream."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "This stream's format is not supported."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            "This TV can't decode this channel's video or sound (for example MPEG-2 broadcast video or AC-4 audio)."
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED, PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR, PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED ->
            "This channel is encrypted (DRM) and can't be played here."
        else -> "Playback failed (${e.errorCodeName})."
    }

    private fun showError(msg: String) {
        spinner.visibility = View.GONE
        errorText.text = "${list[index].name}\n$msg"
        errorBox.visibility = View.VISIBLE
    }

    private fun showInfo() {
        val ch = list[index]
        overlay.bindChannel(ch)
        scope.launch {
            val nn = withContext(Dispatchers.IO) { repo.nowNext(listOf(ch))[ch.key] }
            if (list.getOrNull(index)?.key == ch.key) overlay.bindProgrammes(nn?.first, nn?.second)
        }
        overlay.show()
    }

    // ---- statistics -------------------------------------------------------------------------------

    private val statsTick = object : Runnable {
        override fun run() {
            if (showStats) stats.text = statsText()
            main.postDelayed(this, 1000)
        }
    }

    private fun statsText(): String {
        val p = player ?: return ""
        val v = p.videoFormat; val a = p.audioFormat
        val lines = ArrayList<String>()
        lines += "Video   " + (v?.let { "${it.width}×${it.height}" + fps(it) + "  " + codec(it.sampleMimeType) } ?: "–")
        lines += "Audio   " + (a?.let { codec(it.sampleMimeType) + "  " + channels(it.channelCount) + (if (it.sampleRate > 0) "  ${it.sampleRate / 1000} kHz" else "") } ?: "–")
        val br = (v?.bitrate ?: Format.NO_VALUE).takeIf { it > 0 }
        lines += "Bitrate " + (br?.let { mbps(it) } ?: "–")
        lines += "Buffer  " + String.format(Locale.ROOT, "%.1f s", p.totalBufferedDuration / 1000f)
        lines += "Dropped " + (p.videoDecoderCounters?.droppedBufferCount ?: 0)
        lines += "Cap     " + (if (maxHeight > 0) "${maxHeight}p" else "none")
        lines += "Source  " + (list.getOrNull(index)?.streams?.getOrNull(streamIndex)?.sourceName ?: "–")
        return lines.joinToString("\n")
    }

    private fun fps(f: Format) = if (f.frameRate > 0) String.format(Locale.ROOT, " @%.0f", f.frameRate) else ""
    private fun codec(mime: String?) = when (mime) {
        MimeTypes.VIDEO_H264 -> "H.264"; MimeTypes.VIDEO_H265 -> "HEVC"; MimeTypes.VIDEO_VP9 -> "VP9"; MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"; MimeTypes.AUDIO_AAC -> "AAC"; MimeTypes.AUDIO_AC3 -> "AC-3"; MimeTypes.AUDIO_E_AC3 -> "E-AC-3"
        MimeTypes.AUDIO_MPEG -> "MP3"; MimeTypes.AUDIO_OPUS -> "Opus"
        else -> mime?.substringAfter('/')?.uppercase(Locale.ROOT) ?: "–"
    }
    private fun channels(n: Int) = when (n) { 1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> if (n > 0) "${n}ch" else "" }
    private fun mbps(bps: Int) = if (bps >= 1_000_000) String.format(Locale.ROOT, "%.1f Mb/s", bps / 1e6) else "${bps / 1000} kb/s"

    // ---- keys -------------------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (panel.isOpen) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_GUIDE -> { panel.close(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { panel.step(-1); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { panel.step(1); return true }
            }
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { tune(index + 1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { tune(index - 1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_GUIDE -> { panel.open(); return true }
            KeyEvent.KEYCODE_LAST_CHANNEL -> { lastChannel(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> { showInfo(); return true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CAPTIONS -> { menu(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { event.startTracking(); return true }
        }
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) { digit(keyCode - KeyEvent.KEYCODE_0); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { menu(); return true }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!panel.isOpen && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (event.isTracking && !event.isCanceled) {
                if (errorBox.visibility == View.VISIBLE) tune(index, remember = false) else showInfo()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun lastChannel() { if (previous >= 0) tune(previous) else overlay.flash("No previous channel yet") }

    /** Number entry: digits gather for 1.5 s, then the channel with that number is tuned. */
    private fun digit(d: Int) {
        digits = (digits + d).takeLast(5)
        overlay.flash(digits)
        main.removeCallbacks(tuneDigits)
        main.postDelayed(tuneDigits, 1500)
    }

    private val tuneDigits = Runnable {
        val i = list.indexOfFirst { it.number == digits }.takeIf { it >= 0 } ?: list.indexOfFirst { it.number?.substringBefore('.') == digits }
        if (i >= 0) tune(i) else overlay.flash("No channel $digits")
        digits = ""
    }

    // ---- menu -------------------------------------------------------------------------------------

    private fun choose(title: String, labels: List<String>, pick: (Int) -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setItems(labels.toTypedArray()) { _, i -> pick(i) }.show()
    }

    private fun menu() {
        val ch = list[index]
        val fav = ch.key in repo.favorites.value
        val qualityLabel = if (maxHeight > 0) "${maxHeight}p" else "Original"
        val resizeLabel = when (resize) { AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"; AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"; else -> "Fit" }
        val items = listOf(
            "Audio and subtitles",
            "Video quality · $qualityLabel",
            "Picture · $resizeLabel",
            "Statistics · ${if (showStats) "on" else "off"}",
            if (fav) "Remove from favourites" else "Add to favourites",
            "Previous channel",
            "Channel list",
            "Reload stream",
        )
        choose(ch.name, items) { i ->
            when (i) {
                0 -> tracksDialog()
                1 -> {
                    val caps = listOf(0, 1080, 720, 480, 360)
                    choose("Video quality", caps.map { (if (it == maxHeight) "● " else "   ") + if (it == 0) "Original (no cap)" else "Up to ${it}p" }) { q ->
                        maxHeight = caps[q]; selector?.let { applyQuality(it) }; overlay.flash("Video quality: ${if (caps[q] == 0) "original" else "up to ${caps[q]}p"}")
                    }
                }
                2 -> {
                    val modes = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
                    val names = listOf("Fit (whole picture)", "Zoom (fill, crop the edges)", "Stretch (fill the screen)")
                    choose("Picture", names.mapIndexed { k, n -> (if (modes[k] == resize) "● " else "   ") + n }) { m ->
                        resize = modes[m]; playerView.resizeMode = modes[m]
                    }
                }
                3 -> { showStats = !showStats; stats.visibility = if (showStats) View.VISIBLE else View.GONE; stats.text = statsText() }
                4 -> { val on = repo.toggleFavorite(ch.key); overlay.flash(if (on) "Added to favourites" else "Removed from favourites") }
                5 -> lastChannel()
                6 -> panel.open()
                7 -> tune(index, remember = false)
            }
        }
    }

    /** Audio and subtitle tracks the stream offers, as one D-pad list. */
    private fun tracksDialog() {
        val p = player ?: return
        data class Opt(val label: String, val apply: () -> Unit)
        val opts = ArrayList<Opt>()
        val groups = p.currentTracks.groups
        groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { g ->
            for (t in 0 until g.length) {
                if (!g.isTrackSupported(t)) continue
                val f = g.getTrackFormat(t)
                val mark = if (g.isTrackSelected(t)) "● " else "   "
                opts += Opt(mark + "Audio: " + (f.label ?: f.language ?: "Track ${opts.size + 1}") + (f.channelCount.takeIf { it > 0 }?.let { " · ${channels(it)}" } ?: "")) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, t)).build()
                }
            }
        }
        val text = groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val textOn = text.any { it.isSelected }
        opts += Opt((if (!textOn) "● " else "   ") + "Subtitles: off") {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        }
        text.forEach { g: Tracks.Group ->
            for (t in 0 until g.length) {
                if (!g.isTrackSupported(t)) continue
                val f = g.getTrackFormat(t)
                opts += Opt((if (g.isTrackSelected(t)) "● " else "   ") + "Subtitles: " + (f.label ?: f.language ?: "Track ${t + 1}")) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, t)).build()
                }
            }
        }
        if (text.isEmpty()) opts += Opt("   No subtitles or captions in this stream") {}
        choose("Audio and subtitles", opts.map { it.label }) { i -> opts[i].apply() }
    }

    /** Hands the channel list to the player without putting thousands of entries through an Intent. */
    object Queue {
        var channels: List<Channel> = emptyList()
        var index = 0
    }

    companion object {
        fun start(c: Context, list: List<Channel>, index: Int) {
            Queue.channels = list
            Queue.index = index
            c.startActivity(Intent(c, PlayerActivity::class.java))
        }
    }

    /**
     * The channel banner along the bottom: logo, number and name, a LIVE badge with the group and the
     * clock, a row of technical chips (resolution, codecs, source), and now and next. Hides after a
     * few seconds.
     */
    private inner class InfoOverlay(c: Context) : LinearLayout(c) {
        private val logo = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        private val live = Ui.text(c, "LIVE", 12f, Ui.TEXT, bold = true).apply {
            background = Ui.rounded(c, Ui.ACCENT, 6); setPadding(dp(8), dp(3), dp(8), dp(3)); letterSpacing = 0.12f
        }
        private val group = Ui.text(c, "", 14f, Ui.DIM).apply { letterSpacing = 0.1f }
        private val clock = android.widget.TextClock(c).apply { typeface = Ui.face(c); textSize = 18f; setTextColor(Ui.TEXT) }
        private val title = Ui.text(c, "", 34f, bold = true)
        private val chips = Ui.horizontal(c, 8)
        private val now = Ui.text(c, "", 18f)
        private val next = Ui.text(c, "", 15f, Ui.DIM)
        private val note = Ui.text(c, "", 16f, Ui.ACCENT, bold = true)
        private val hide = Runnable { animate().alpha(0f).setDuration(if (Ui.animations(context)) 250 else 0).start() }

        init {
            orientation = VERTICAL
            setPadding(dp(64), dp(64), dp(64), dp(40))
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xF0000000.toInt(), 0xA0000000.toInt(), 0x00000000))
            val top = Ui.horizontal(c, 12).apply { gravity = Gravity.CENTER_VERTICAL }
            top.addView(live); top.addView(group)
            top.addView(View(c), Ui.lp(0, 1, 1f)); top.addView(clock)
            addView(note)
            addView(top, Ui.lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(10) })
            val head = Ui.horizontal(c, 18).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(logo, LinearLayout.LayoutParams(dp(120), dp(68)))
            val words = Ui.vertical(c, 6)
            words.addView(title); words.addView(chips); words.addView(now); words.addView(next)
            head.addView(words, Ui.lp(0, Ui.WRAP, 1f))
            addView(head)
            alpha = 0f
        }

        private fun chip(s: String) = Ui.text(context, s, 13f, Ui.TEXT).apply {
            background = Ui.rounded(context, 0x33FFFFFF, 8, 0x40FFFFFF, 1); setPadding(dp(10), dp(4), dp(10), dp(4))
        }

        fun bindChannel(ch: Channel) {
            title.text = listOfNotNull(ch.number, ch.name).joinToString("   ")
            group.text = listOfNotNull(ch.group?.replace(";", " · ")?.uppercase(Locale.ROOT), "${index + 1} / ${list.size}").joinToString("  ·  ")
            now.text = if (ch.epgIds.isEmpty()) "No guide information" else ""
            next.text = ""; note.text = ""
            app.logos.load(ch.logo, logo, dp(68)) { shown -> logo.visibility = if (shown) View.VISIBLE else View.GONE }
            bindTech()
        }

        /** Resolution, codecs and source as chips, refreshed when the tracks are known. */
        fun bindTech() {
            val p = player
            chips.removeAllViews()
            val v = p?.videoFormat; val a = p?.audioFormat
            if (v != null && v.height > 0) {
                chips.addView(chip(when {
                    v.height >= 2160 -> "4K"; v.height >= 1080 -> "FHD"; v.height >= 720 -> "HD"; else -> "SD"
                }))
                chips.addView(chip("${v.width}×${v.height}" + fps(v)))
                chips.addView(chip(codec(v.sampleMimeType)))
            }
            if (a != null) chips.addView(chip(codec(a.sampleMimeType) + channels(a.channelCount).let { if (it.isEmpty()) "" else " $it" }))
            list.getOrNull(index)?.streams?.getOrNull(streamIndex)?.let { chips.addView(chip(it.sourceName)) }
            if (list.getOrNull(index)?.key in repo.favorites.value) chips.addView(chip("★ Favourite"))
        }

        fun bindProgrammes(cur: Programme?, nx: Programme?) {
            if (cur != null) now.text = "Now  ${Ui.time(context, cur.start)} – ${Ui.time(context, cur.stop)}   ${cur.title}"
            if (nx != null) next.text = "Next  ${Ui.time(context, nx.start)}   ${nx.title}"
        }

        fun show() { alpha = 1f; main.removeCallbacks(hide); main.postDelayed(hide, 6000) }
        fun flash(msg: String) { note.text = msg; show() }
    }

    /**
     * The channel list over the picture, on Left: one category at a time (the first word of a playlist's
     * "a;b;c" group), its channels by number and name with the playing one marked. Left and Right step
     * through the categories, "All channels" first; OK tunes and closes; Back closes.
     */
    private inner class ChannelPanel(c: Context) : LinearLayout(c) {
        private val header = Ui.text(c, "", 14f, Ui.DIM, bold = true).apply { letterSpacing = 0.14f }
        private val hint = Ui.text(c, "‹ ›  category    OK  watch    Back  close", 12f, Ui.DIM)
        private val rv = RecyclerView(c)
        private var items: List<Int> = emptyList()
        private var categories: List<String?> = emptyList()
        private var category = 0
        var isOpen = false; private set

        init {
            orientation = VERTICAL
            setPadding(dp(28), dp(36), dp(20), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xF20A0A0C.toInt(), 0xE60A0A0C.toInt(), 0x000A0A0C))
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(6) })
            addView(hint, Ui.lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })
            rv.layoutManager = LinearLayoutManager(c)
            rv.clipToPadding = false
            rv.setPadding(dp(6), dp(6), dp(30), dp(6))
            rv.adapter = Adapter()
            addView(rv, Ui.lp(Ui.MATCH, 0, 1f))
            visibility = View.GONE
        }

        private fun mainCategory(ch: Channel) = ch.group?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }

        fun open() {
            // null stands for "All channels"; then every category in the order the list first shows it.
            categories = listOf<String?>(null) + list.mapNotNull { mainCategory(it) }.distinct()
            category = categories.indexOf(mainCategory(list[index])).coerceAtLeast(0)
            visibility = View.VISIBLE; isOpen = true
            this@PlayerActivity.overlay.alpha = 0f
            fill(focusPlaying = true)
        }

        /** Left/Right inside the panel: the previous or next category. */
        fun step(delta: Int) {
            category = Math.floorMod(category + delta, categories.size)
            fill(focusPlaying = false)
        }

        private fun fill(focusPlaying: Boolean) {
            val g = categories[category]
            items = list.indices.filter { g == null || mainCategory(list[it]) == g }
            header.text = "‹  " + ((g ?: "All channels").uppercase(Locale.ROOT)) + "  ·  ${items.size}  ›"
            rv.adapter?.notifyDataSetChanged()
            val at = if (focusPlaying) items.indexOf(index).coerceAtLeast(0) else 0
            (rv.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(at, dp(160))
            rv.post { rv.findViewHolderForAdapterPosition(at)?.itemView?.requestFocus() ?: rv.requestFocus() }
        }

        fun close() { visibility = View.GONE; isOpen = false }

        private inner class Holder(val row: TextView) : RecyclerView.ViewHolder(row)

        private inner class Adapter : RecyclerView.Adapter<Holder>() {
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
                val row = Ui.text(parent.context, "", 17f).apply {
                    isFocusable = true; isClickable = true
                    background = Ui.focusable(parent.context, 0x00000000, 12)
                    setPadding(dp(16), dp(11), dp(16), dp(11))
                }
                return Holder(row).also { h ->
                    row.setOnClickListener { items.getOrNull(h.bindingAdapterPosition)?.let { close(); tune(it) } }
                }
            }
            override fun onBindViewHolder(h: Holder, position: Int) {
                val i = items[position]
                val ch = list[i]
                h.row.text = (if (i == index) "▶  " else "    ") + listOfNotNull(ch.number, ch.name).joinToString("   ")
                h.row.setTextColor(if (i == index) Ui.ACCENT else Ui.TEXT)
                h.row.layoutParams = RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(4) }
            }
        }
    }
}
