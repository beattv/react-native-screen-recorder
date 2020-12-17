package com.reactnativerecordscreen

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class RecordScreenModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val REQUEST_CODE = 1000
    private var screenDensity: Int = 0;
    private var projectManager: MediaProjectionManager? = null;
    private var mediaProjection: MediaProjection? = null;
    private var virtualDisplay: VirtualDisplay? = null;
    private var mediaRecorder: MediaRecorder? = null;
    private lateinit var mService: RecordScreenService
    private var mBound: Boolean = false

    internal var videoUri: String = "";

    private var screenWidth: Int = 0;
    private var screenHeight: Int = 0;

    private var startPromise: Promise? = null;

    override fun getName(): String {
        return "RecordScreen"
    }

    val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as RecordScreenService.LocalBinder
            mService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private val mActivityEventListener: ActivityEventListener = object : BaseActivityEventListener() {
      override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode != REQUEST_CODE) {
            startPromise?.reject("RECORDING_WRONG_CODE", "Wrong request code.")
            return
        }

        if (resultCode != RESULT_OK) {
            startPromise?.reject("RECORDING_REJECTED.", "User rejected recording");
            return
        }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              val captureIntent = Intent(reactApplicationContext, RecordScreenService::class.java).apply {
                  reactApplicationContext.bindService(this, connection, Context.BIND_AUTO_CREATE)
                  action = RecordScreenService.ACTION_START
                  putExtra(RecordScreenService.EXTRA_RESULT_DATA, data!!)
                  putExtra(RecordScreenService.SCREEN_WIDTH_DATA, screenWidth)
                  putExtra(RecordScreenService.SCREEN_HEIGHT_DATA, screenHeight)
                  putExtra(RecordScreenService.SCREEN_DENSITY_DATA, screenDensity)
              }
              ContextCompat.startForegroundService(reactApplicationContext, captureIntent)
              print("started recording")
              startPromise?.resolve(null)

              return
          }

          initRecorder()
        mediaProjection = projectManager!!.getMediaProjection(resultCode, data)
        mediaProjection!!.registerCallback(MediaProjectionCallback(), null)
        virtualDisplay = createVirtualDisplay()
        mediaRecorder?.start()

        startPromise?.resolve(null)
      }
    }

    init {
      reactContext.addActivityEventListener(mActivityEventListener);
    }

    inner class MediaProjectionCallback: MediaProjection.Callback() {
      override fun onStop() {
        // ボタンが押されたら
        // super.onStop()
        mediaRecorder!!.stop();
        mediaRecorder!!.reset();

        mediaProjection = null;

      }
    }

    @ReactMethod
    fun setup(readableMap: ReadableMap) {
      projectManager = this.reactApplicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

      val metrics = DisplayMetrics()
      reactApplicationContext.currentActivity?.windowManager?.defaultDisplay?.getMetrics(metrics)
      screenDensity = metrics.densityDpi
      screenWidth = 720
      screenHeight = 1280
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
      return mediaProjection?.createVirtualDisplay("ScreenSharing",
              screenWidth, screenHeight, screenDensity,
              DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
              mediaRecorder?.getSurface(), null /*Callbacks*/, null /*Handler*/)
    }

    private fun shareScreen() {
      if (mediaProjection == null) {
        val i = projectManager!!.createScreenCaptureIntent()
        this.currentActivity!!.startActivityForResult(i, REQUEST_CODE)
        return
      }

      virtualDisplay = createVirtualDisplay()
      mediaRecorder!!.start()

      startPromise?.resolve(null)
    }

    @ReactMethod
    fun startRecording(promise: Promise) {
      startPromise = promise

      shareScreen()
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
                .append(SimpleDateFormat("dd-MM-yyyy-hh_mm_ss").format(Date()))
                .append(".mp4")
                .toString();

        mediaRecorder!!.setOutputFile(videoUri)

        mediaRecorder!!.prepare()
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }

    @ReactMethod
    fun stopRecording(promise: Promise) {
      try {
          println("stopping")
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              videoUri = mService.stop()
          } else {
              mediaRecorder!!.stop();
              mediaRecorder!!.release();
          }

        val response = WritableNativeMap();
        val result = WritableNativeMap();
        result.putString("videoUrl", videoUri);
        response.putString("status", "success");
        response.putMap("result", result);

        promise.resolve(response);
      } catch (err: RuntimeException) {
        err.printStackTrace();
        promise.reject(err)
      }
    }

    @ReactMethod
    fun clean(promise: Promise) {
        println("clean");
        promise.resolve(null);
    }

}
