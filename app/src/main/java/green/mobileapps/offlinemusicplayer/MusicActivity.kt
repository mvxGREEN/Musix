package green.mobileapps.offlinemusicplayer

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MusicActivity : AppCompatActivity() {

    private val TAG = "MusicActivity"
    private lateinit var playerView: PlayerView
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null // Store the connected controller
    // 2. Define the Player.Listener
    private val playerListener = object : Player.Listener {
        // This callback is triggered when the media item (track) changes
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            Log.d(TAG, "onMediaItemTransition: New track detected. Updating metadata UI.")
            updateMetadataUI(mediaItem)
        }
    }

    // UI Elements for metadata
    private lateinit var textTitle: TextView
    private lateinit var textArtist: TextView
    private lateinit var imageAlbumArt: ImageView // New Album Art ImageView

    // Stored Audio File no longer needed, relying on MediaController / Repository
    // private var currentAudioFile: AudioFile? = null

    // Intent extra key is now unused for data, only kept the field
    private val EXTRA_AUDIO_FILE = "EXTRA_AUDIO_FILE"


    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.music_activity)

        // --- UI Initialization ---
        playerView = findViewById(R.id.player_view)
        textTitle = findViewById(R.id.text_track_title)
        textArtist = findViewById(R.id.text_track_artist)

        // The XML now handles the PlayerView sizing, but we ensure all buttons are visible
        playerView.setShowFastForwardButton(true)
        playerView.setShowRewindButton(true)
        playerView.setShowNextButton(true)
        playerView.setShowPreviousButton(true)
        playerView.setShowShuffleButton(true)

        //displayInitialTrackMetadata()
    }

    /**
     * Updates the UI (Title/Artist) based on the current MediaItem's metadata.
     */
    private fun updateMetadataUI(mediaItem: androidx.media3.common.MediaItem?) {
        val metadata = mediaItem?.mediaMetadata
        if (metadata != null) {
            // Title
            val title = metadata.title?.toString() ?: "Unknown Title"
            textTitle.text = title

            // Artist and Album
            val artist = metadata.artist?.toString() ?: "Unknown Artist"
            val album = metadata.albumTitle?.toString()
            val artistText = if (album.isNullOrBlank()) artist else "$artist • $album"
            textArtist.text = artistText

            // TODO: Add album art loading here if the MediaItem has artwork URI/Bitmap.

        } else {
            // Fallback for a disconnected or empty state
            textTitle.text = "No Track Loaded"
            textArtist.text = "Waiting for Media Service"

            // go back to the MainActivity
            onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Creating MediaController")

        val sessionToken = SessionToken(
            this,
            ComponentName(this, MusicService::class.java)
        )

        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                playerView.player = mediaController
                // 3. Attach the listener to the connected controller
                mediaController?.addListener(playerListener)
                Log.d(TAG, "MediaController connected, attached to PlayerView, and listener added.")

                // 4. Update the UI with the *current* track immediately upon connection
                updateMetadataUI(mediaController?.currentMediaItem)

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting MediaController", e)
                // Fallback display if connection fails
                textTitle.text = "Connection Failed"
                textArtist.text = "Check if MusicService is running"
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        if (controllerFuture.isDone) {
            // 5. Safely remove the listener and release the controller
            mediaController?.removeListener(playerListener)
            playerView.player = null
            MediaController.releaseFuture(controllerFuture)
            Log.d(TAG, "MediaController listener removed and controller released.")
        }
        mediaController = null
    }
}