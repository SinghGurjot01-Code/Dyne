package com.dyne.integrations.music

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.dyne.dyne
import com.dyne.events.EventType
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.integrations.Integration
import com.dyne.integrations.notifications.DyneNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tracks active MediaSessions. Android only exposes other apps' sessions to apps that hold
 * notification access, so this relies on the NotificationListenerService being enabled.
 */
class MusicMonitor(private val ctx: Context, private val events: IslandEventManager) : Integration {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(ctx, DyneNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var registered: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private var lastKey: String? = null
    private var lastPlaying = false
    private var pauseJob: Job? = null
    private var uriArt: Pair<String, Bitmap>? = null
    private var running = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = reselect()
        override fun onMetadataChanged(metadata: MediaMetadata?) = reselect()
        override fun onSessionDestroyed() = reselect()
    }

    override fun start() {
        running = true
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            attach(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            running = false // notification access not granted
        }
    }

    override fun stop() {
        running = false
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        registered.forEach { runCatching { it.unregisterCallback(callback) } }
        registered = emptyList(); current = null; lastKey = null; lastPlaying = false
        pauseJob?.cancel()
        events.clear(EventType.MUSIC)
    }

    private fun attach(list: List<MediaController>) {
        registered.forEach { runCatching { it.unregisterCallback(callback) } }
        registered = list
        list.forEach { it.registerCallback(callback, handler) }
        reselect()
    }

    private fun reselect() {
        if (!running) return
        val list = registered
        current = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.sessionToken == current?.sessionToken && it.metadata != null }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED && it.metadata != null }
        publish()
    }

    private fun publish() {
        val c = current
        val st = c?.playbackState
        val md = c?.metadata
        val state = st?.state
        if (c == null || st == null || md == null || state == PlaybackState.STATE_STOPPED ||
            state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_ERROR
        ) {
            pauseJob?.cancel(); lastPlaying = false; lastKey = null
            events.clear(EventType.MUSIC); return
        }
        val playing = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Unknown title"
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artUri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val art = bmp ?: uriArt?.takeIf { it.first == artUri }?.second
        if (art == null && artUri != null && uriArt?.first != artUri) loadUriArt(artUri)

        val key = "${c.sessionToken.hashCode()}|$title|$artist"
        val expand = playing && (!lastPlaying || key != lastKey)
        lastPlaying = playing; lastKey = key

        events.setPersistent(
            IslandEvent.Music(
                title = title, artist = artist, art = art, playing = playing,
                positionMs = st.position, durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                updatedAt = st.lastPositionUpdateTime, speed = st.playbackSpeed,
                canNext = st.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                canPrev = st.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                controller = c
            ),
            expand = expand
        )
        pauseJob?.cancel()
        if (!playing) pauseJob = ctx.dyne.appScope.launch {
            delay(8_000) // paused: return to the camera-only pill promptly
            events.clear(EventType.MUSIC)
        }
    }

    /** Only local content:// and file:// art is decoded; the app has no network access. */
    private fun loadUriArt(uri: String) {
        ctx.dyne.appScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val u = Uri.parse(uri)
                    if (u.scheme != "content" && u.scheme != "file") null
                    else ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bmp != null) { uriArt = uri to bmp; if (running) publish() }
        }
    }
}
