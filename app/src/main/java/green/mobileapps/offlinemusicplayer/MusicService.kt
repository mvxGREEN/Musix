package green.mobileapps.offlinemusicplayer

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult // <-- NEW EXPLICIT IMPORT
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture

class MusicService : MediaLibraryService() {

    private lateinit var player: Player
    private lateinit var mediaLibrarySession: MediaLibrarySession

    // Temporary storage for the last loaded file information
    private var lastLoadedFile: AudioFile? = null

    // Helper extension to convert our AudioFile model to Media3's MediaItem
    private fun AudioFile.toMediaItem(): MediaItem {
        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setGenre(genre)

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize ExoPlayer (The Player)
        player = ExoPlayer.Builder(this).build()

        // 2. Initialize MediaLibrarySession (The Controller Interface)
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, CustomMediaLibrarySessionCallback())
            .setSessionActivity(getMediaSessionActivity()!!)
            .build()

        // Media3 handles the Foreground Service start automatically when playback begins
        // and manages the notification (including controls and progress) via the player state.
    }

    // --- MediaLibraryService Overrides ---

    // 3. Provide the Session to the system
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    // 4. Handle initial playback request from MainActivity (via Intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val file: AudioFile? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("EXTRA_AUDIO_FILE", AudioFile::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("EXTRA_AUDIO_FILE")
        }

        if (file != null && file != lastLoadedFile) {
            lastLoadedFile = file
            // Stop any current playback
            player.stop()
            // Load the new media item and start preparation
            player.setMediaItem(file.toMediaItem())
            player.prepare()
            player.play()
        } else if (file == null && intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            // This allows Media3 to handle media button presses (e.g., headset controls)
            return super.onStartCommand(intent, flags, startId)
        } else if (player.playbackState == Player.STATE_IDLE && lastLoadedFile != null) {
            // Restart playback if the service was stopped but the last file is known
            player.setMediaItem(lastLoadedFile!!.toMediaItem())
            player.prepare()
            player.play()
        }

        // Return START_STICKY to ensure the service restarts if terminated by the system
        return START_STICKY
    }

    // 5. Cleanup when service is destroyed
    override fun onDestroy() {
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    // --- Media3 Custom Callback for Handling System/Controller Requests ---

    @OptIn(UnstableApi::class)
    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult { // <-- RETURN TYPE IS NOW JUST 'ConnectionResult'
            // Allow the client (e.g., MainActivity, system notification) to connect and send basic commands
            val availableCommands = SessionCommands.Builder()
                .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)) // Allows browsing (basic library capability)
                .build()

            val playerCommands = Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_STOP)
                .build()

            // FIXED: Using ConnectionResult.Builder due to explicit import
            return MediaSession.ConnectionResult.accept(
                SessionCommands.Builder().build(),
                player.availableCommands
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // This is primarily for when the client wants to add a playlist.
            // Since we only support playing one file at a time, we return the original list.
            return super.onAddMediaItems(mediaSession, controller, mediaItems)
        }
    }

    // --- Helper for Notification Intent (Crucial for opening app from notification) ---

    private fun getMediaSessionActivity(): PendingIntent? {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
