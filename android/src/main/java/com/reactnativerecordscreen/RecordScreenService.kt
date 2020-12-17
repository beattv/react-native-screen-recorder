package com.reactnativerecordscreen

/*
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * This project and source code may use libraries or frameworks that are
 * released under various Open-Source licenses. Use of those libraries and
 * frameworks are governed by their own individual licenses.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */



import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.icu.text.SimpleDateFormat
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread
import kotlin.experimental.and

class RecordScreenService : Service() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null;
    internal var videoUri: String = "";
    private val binder = LocalBinder()

    private lateinit var audioCaptureThread: Thread
    private var audioRecord: AudioRecord? = null

    override fun onCreate() {
        super.onCreate()

        // TODO: Add code for starting the service and getting notifications
        createNotificationChannel()

        startForeground(SERVICE_ID, NotificationCompat.Builder(this,
                NOTIFICATION_CHANNEL_ID).build())

        mediaProjectionManager = applicationContext.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): RecordScreenService = this@RecordScreenService
    }

    override fun onBind (intent: Intent): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
                "Record Screen Service Channel", NotificationManager.IMPORTANCE_DEFAULT)

        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (intent != null) {
            when (intent.action) {
                ACTION_START -> {
                    mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, intent.getParcelableExtra(EXTRA_RESULT_DATA)!!) as MediaProjection

                    initRecorder()
                    createVirtualDisplay(intent.getIntExtra(SCREEN_WIDTH_DATA, 0), intent.getIntExtra(SCREEN_HEIGHT_DATA, 0), intent.getIntExtra(SCREEN_DENSITY_DATA, 0))
                    mediaRecorder!!.start()
                    Service.START_STICKY
                }
                else -> throw IllegalArgumentException("Unexpected action received: ${intent.action}")
            }
        } else {
            Service.START_NOT_STICKY
        }
    }

    private fun createVirtualDisplay(screenWidth: Int, screenHeight: Int, screenDensity: Int): VirtualDisplay? {
        return mediaProjection?.createVirtualDisplay("ScreenSharing",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.getSurface(), null /*Callbacks*/, null /*Handler*/)
    }

    private fun initRecorder() {
        try {
            mediaRecorder = MediaRecorder()
            mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)

            val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P)

            mediaRecorder!!.setOutputFormat(profile.fileFormat);
            // mediaRecorder!!.setOrientationHint(90)
            mediaRecorder!!.setVideoFrameRate(profile.videoFrameRate);
            mediaRecorder!!.setVideoSize(profile.videoFrameHeight, profile.videoFrameWidth);
            mediaRecorder!!.setVideoEncodingBitRate(profile.videoBitRate);
            mediaRecorder!!.setVideoEncoder(profile.videoCodec);
            mediaRecorder!!.setAudioEncodingBitRate(profile.audioBitRate);
            mediaRecorder!!.setAudioChannels(profile.audioChannels);
            mediaRecorder!!.setAudioSamplingRate(profile.audioSampleRate);
            mediaRecorder!!.setAudioEncoder(profile.audioCodec);

            videoUri = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    .toString() + StringBuilder("/")
                    .append("screen_recording-")
                    .append(java.text.SimpleDateFormat("dd-MM-yyyy-hh_mm_ss").format(Date()))
                    .append(".mp4")
                    .toString();

            mediaRecorder!!.setOutputFile(videoUri)

            mediaRecorder!!.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    public fun stop (): String {
        try {
            mediaRecorder!!.stop();
            mediaRecorder!!.release();
        } catch (err: RuntimeException) {
            println("neco spatne")
            println(err)
        }

        return videoUri
    }

    companion object {
        private const val LOG_TAG = "AudioCaptureService"
        private const val SERVICE_ID = 123
        private const val NOTIFICATION_CHANNEL_ID = "AudioCapture channel"

        private const val NUM_SAMPLES_PER_READ = 1024
        private const val BYTES_PER_SAMPLE = 2 // 2 bytes since we hardcoded the PCM 16-bit format

        const val ACTION_START = "RecordScreenService:Start"
        const val EXTRA_RESULT_DATA = "RecordScreenService:Extra:ResultData"
        const val SCREEN_WIDTH_DATA = "RecordScreenService:Extra:ScreenWidthData"
        const val SCREEN_HEIGHT_DATA = "RecordScreenService:Extra:ScreenHeightData"
        const val SCREEN_DENSITY_DATA = "RecordScreenService:Extra:ScreenDensityData"
    }
}
