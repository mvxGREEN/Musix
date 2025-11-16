package green.mobileapps.offlinemusicplayer

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
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

        // --- Remove Intent Logic ---
        // We no longer retrieve AudioFile from Intent.
        // The display will be updated when MediaController connects, but we show
        // the initial track from the Repository as a fallback/initial state.

        displayInitialTrackMetadata()
    }

    /**
     * Updates the UI with metadata from the received AudioFile in the Repository.
     */
    private fun displayInitialTrackMetadata() {
        // Retrieve the current playing track from the shared repository
        val file = PlaylistRepository.getCurrentTrack()
        if (file != null) {
            val trackPrefix = if (file.track != null && file.track > 0) "${file.track}. " else ""
            // Display Title
            textTitle.text = "$trackPrefix${file.title}"

            // Display Artist and Album, separated by a distinct dot
            val albumInfo = if (file.album != null) " • ${file.album}" else ""
            textArtist.text = "${file.artist}$albumInfo"

            // TODO: Add logic here to load actual album art into imageAlbumArt if available in AudioFile
        } else {
            textTitle.text = "Loading..."
            textArtist.text = "Connect to Media Service"
        }
    }

    // --- MediaController Setup ---

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
                val mediaController = controllerFuture.get()
                playerView.player = mediaController
                Log.d(TAG, "MediaController connected and attached to PlayerView.")
                // PlayerView will now automatically update based on the player state/metadata

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
            val mediaController = controllerFuture.get()
            playerView.player = null
            MediaController.releaseFuture(controllerFuture)
            Log.d(TAG, "MediaController released.")
        }
    }
}