package com.aamir.my_music.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aamir.my_music.R
import com.aamir.my_music.Track
import com.aamir.my_music.application.CHANNEL_ID
import com.aamir.my_music.songs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PREV = "previous"
const val NEXT = "next"
const val PLAY_PAUSE = "play_pause"

class MusicPlayerService : Service() {

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {

        fun getService() = this@MusicPlayerService

        fun setMusicList(list: List<Track>) {
            this@MusicPlayerService.musicList = list.toMutableList()
        }

        fun currentDuration() = this@MusicPlayerService.currentDuration
        fun maxDuration() = this@MusicPlayerService.maxDuration
        fun isPlaying() = this@MusicPlayerService.isPlaying
        fun currentTrack() = this@MusicPlayerService.currentTrack
    }

    private var mediaPlayer = MediaPlayer()
    private val currentTrack = MutableStateFlow(Track())
    private var musicList = mutableListOf<Track>()

    private var maxDuration = MutableStateFlow(0f)
    private var currentDuration = MutableStateFlow(0f)

    private var scope = CoroutineScope(Dispatchers.Main)

    private var job: Job? = null

    private val isPlaying = MutableStateFlow(false)

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (intent.action) {
                PREV -> {
                    prev()
                }
                PLAY_PAUSE -> {
                    playPause()
                }
                NEXT -> {
                    next()
                }
                else -> {
                    currentTrack.update { songs[0] }
                    play(currentTrack.value)
                }
            }
        }

        return START_STICKY
    }

    private fun updateDuration(){
        job = scope.launch {
            if (mediaPlayer.isPlaying.not()) return@launch

            maxDuration.update { mediaPlayer.duration.toFloat() }

            while (true){
                currentDuration.update { mediaPlayer.currentPosition.toFloat() }
                delay(1000)
            }
        }
    }

    fun prev(){
        job?.cancel()
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        val index = musicList.indexOf(currentTrack.value)
        val prevIndex = if (index < 0) musicList.size.minus(1) else index.minus(1)
        val prevItem = musicList[prevIndex]
        currentTrack.update { prevItem }
        mediaPlayer.setDataSource(this, getRawUri(currentTrack.value.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(currentTrack.value)
            updateDuration()
        }
    }

    fun next(){
        job?.cancel()
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        val index = musicList.indexOf(currentTrack.value)
        val nextIndex = index.plus(1).mod(musicList.size)
        val nextItem = musicList[nextIndex]
        currentTrack.update { nextItem }
        mediaPlayer.setDataSource(this, getRawUri(nextItem.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(currentTrack.value)
            updateDuration()
        }
    }

    fun playPause(){
        if (mediaPlayer.isPlaying){
            mediaPlayer.pause()
        } else{
            mediaPlayer.start()
        }
        sendNotification(currentTrack.value)
    }

    private fun play(track: Track){
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(this, getRawUri(track.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(track)
            updateDuration()
        }
    }

    private fun getRawUri(id: Int) = Uri.parse("android.resource://$packageName/${id}")

    private fun sendNotification(track: Track) {
        val session = MediaSessionCompat(this, "music")



        isPlaying.update { mediaPlayer.isPlaying }
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0,1,2).
                setMediaSession(session.sessionToken)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(style)
            .setContentTitle(track.name)
            .setContentText(track.desc)
            .addAction(R.drawable.previous_icon, "Previous", createPrevPendingIntent())
            .addAction(if (mediaPlayer.isPlaying) R.drawable.pause_icon else R.drawable.play_icon, "Play", createPlayPausePendingIntent())
            .addAction(R.drawable.next_icon, "Next", createNextPendingIntent())
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.music_notification)).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startForeground(1, notification)
            }
        } else {
            startForeground(1, notification)
        }
    }

    private fun createPrevPendingIntent() : PendingIntent {
        val prevIntent = Intent(this, MusicPlayerService::class.java).setAction(PREV)
        return PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createNextPendingIntent() : PendingIntent {
        val nextIntent = Intent(this, MusicPlayerService::class.java).setAction(NEXT)
        return PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createPlayPausePendingIntent() : PendingIntent {
        val playPauseIntent = Intent(this, MusicPlayerService::class.java).setAction(PLAY_PAUSE)
        return PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}