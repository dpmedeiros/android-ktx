package androidx.media

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
import android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
import android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import android.support.v4.media.session.PlaybackStateCompat.State

@DslMarker
annotation class MediaSessionDslMarker

@MediaSessionDslMarker
interface Canceler {
    /**
     * Unregisters the listener
     */
    fun stopListening()
}

@MediaSessionDslMarker
interface MediaEventDsl : Canceler {
    /**
     * Convenience to get the current position without referencing the [playbackState] explicitly
     */
    val position: Long
    /**
     * Convenience to get the playback state extras without referencing the [playbackState] explicitly
     */
    val stateExtras: Bundle?
    /**
     * Returns the extras bundle for the session
     */
    val sessionExtras: Bundle?
    /**
     * Returns the current playback state
     */
    val playbackState: PlaybackStateCompat
}

@MediaSessionDslMarker
interface SessionListenerConfigurator {
    /**
     * Configures a callback function to invoke when playback state changes to [STATE_PLAYING]
     *
     * The callback will be invoked on the main thread
     */
    fun startsPlaying(callback: MediaEventDsl.() -> Unit)

    /**
     * Configures a callback function to invoke when playback state changes to [STATE_STOPPED]
     *
     * The callback will be invoked on the main thread
     */
    fun stopsPlaying(callback: MediaEventDsl.() -> Unit)

    /**
     * Configures a callback function to invoke when playback state changes to [STATE_PAUSED]
     *
     * The callback will be invoked on the main thread
     */
    fun pauses(callback: MediaEventDsl.() -> Unit)

    /**
     * Configures a callback function to invoke when any playback state change occurs
     *
     * The callback will be invoked on the main thread
     *
     * @param callback function that receives the current state
     */
    fun stateChanges(callback: MediaEventDsl.(state: Int) -> Unit)

    /**
     * Configures a callback function to invoke when position changes
     *
     * The callback will be invoked on the main thread
     *
     * @param callback function that receives the current position
     */
    fun positionChanges(callback: MediaEventDsl.(position: Long) -> Unit)

    /**
     * Configures a callback function to invoke when metadata changes
     *
     * The callback will be invoked on the main thread
     *
     * @param callback function that receives the current metadata
     */
    fun metadataChanges(callback: MediaEventDsl.(MediaMetadataCompat) -> Unit)
}

private class SessionListener(private val controller: MediaControllerCompat) :
    MediaControllerCompat.Callback(), MediaEventDsl, SessionListenerConfigurator {

    private var onStateChange: (MediaEventDsl.(state: Int) -> Unit)? = null
    private var onStartsPlaying: (MediaEventDsl.() -> Unit)? = null
    private var onPause: (MediaEventDsl.() -> Unit)? = null
    private var onStopsPlaying: (MediaEventDsl.() -> Unit)? = null
    private var onPositionChanges: (MediaEventDsl.(position: Long) -> Unit)? = null
    private var onMetadataChanges: (MediaEventDsl.(metadata: MediaMetadataCompat) -> Unit)? = null

    @State private var lastState: Int = STATE_NONE
    private var lastPosition: Long = PLAYBACK_POSITION_UNKNOWN

    override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
        if (lastState != state.state) {
            onStateChange?.invoke(this, state.state)
            when (state.state) {
                STATE_PLAYING -> {
                    if (lastState == STATE_STOPPED || lastState == STATE_ERROR) {
                        onStartsPlaying?.invoke(this)
                    }
                }
                STATE_PAUSED -> {
                    onPause?.invoke(this)
                }
                STATE_STOPPED -> {
                    onStopsPlaying?.invoke(this)
                }
            }
            lastState = state.state
        }
        if (lastPosition != state.position) {
            onPositionChanges?.invoke(this, state.position)
            lastPosition = state.position
        }
    }

    override fun onMetadataChanged(metadata: MediaMetadataCompat) {
        onMetadataChanges?.invoke(this, metadata)
    }

    override fun onSessionDestroyed() {
        stopListening()
    }

    override val position: Long
        get() = controller.playbackState.position

    override val stateExtras: Bundle?
        get() = controller.playbackState.extras

    override val playbackState: PlaybackStateCompat
        get() = controller.playbackState

    override val sessionExtras: Bundle?
        get() = controller.extras

    override fun stopListening() {
        controller.unregisterCallback(this)
    }

    override fun stateChanges(callback: MediaEventDsl.(state: Int) -> Unit) {
        onStateChange = callback
    }

    override fun startsPlaying(callback: MediaEventDsl.() -> Unit) {
        onStartsPlaying = callback
    }
    override fun pauses(callback: MediaEventDsl.() -> Unit) {
        onPause = callback
    }
    override fun stopsPlaying(callback: MediaEventDsl.() -> Unit) {
        onStopsPlaying = callback
    }

    override fun positionChanges(callback: MediaEventDsl.(position: Long) -> Unit) {
        onPositionChanges = callback
    }

    override fun metadataChanges(callback: MediaEventDsl.(metadata: MediaMetadataCompat) -> Unit) {
        onMetadataChanges = callback
    }
}

/**
 * Convenience DSL to easily configure event handling on media session state without having to manually register a
 * callback with [MediaControllerCompat.registerCallback]
 *
 * This function registers a listener on media session state.  The listener can be configured via the specified
 * [configure] function to react to events given by [SessionListenerConfigurator]
 *
 * @param configure a function that configures event handling for session state updates
 *
 * @return a [Canceler] object to unregister the listener from outside of state change events
 */
fun MediaSessionCompat.whenSession(configure: SessionListenerConfigurator.() -> Unit): Canceler {
    return controller.whenSession(configure)
}

/**
 * Convenience DSL to easily configure event handling on media session state without having to manually register a
 * callback with [MediaControllerCompat.registerCallback]
 *
 * This function registers a listener on media session state.  The listener can be configured via the specified
 * [configure] function to react to events given by [SessionListenerConfigurator]
 *
 * @param configure a function that configures event handling for session state updates
 *
 * @return a [Canceler] object to unregister the listener from outside of state change events
 */
fun MediaControllerCompat.whenSession(configure: SessionListenerConfigurator.() -> Unit): Canceler {
    val sessionListener = SessionListener(this)
    sessionListener.configure()
    registerCallback(sessionListener, Handler(Looper.getMainLooper()))
    return sessionListener // return object to allow user to cancel
}