package com.aoguzhan.worriedcat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.DisplayMetrics
import android.view.PixelCopy
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aoguzhan.compose.primaryLight
import com.aoguzhan.worriedcat.data.datastore.PreferenceKeys.FAMILIARITY_VALUE
import com.aoguzhan.worriedcat.data.datastore.PreferenceKeys.SELECTED_LABELS
import com.aoguzhan.worriedcat.data.datastore.dataStore
import com.aoguzhan.worriedcat.ml.EfficientdetTfliteLite2DetectionMetadataV1
import com.aoguzhan.worriedcat.utils.parcelable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.function.Consumer

//https://github.com/pkrieter/android-background-screen-recorder/blob/master/app/src/main/java/com/ifib/pkrieter/datarecorder/RecordService.java
//https://github.com/googlearchive/android-ScreenCapture/blob/master/Application/src/main/java/com/example/android/screencapture/ScreenCaptureFragment.java
//https://github.com/mtsahakis/MediaProjectionDemo/blob/master/app/src/main/java/com/mtsahakis/mediaprojectiondemo/ScreenCaptureService.java
//https://github.com/webrtc-uwp/webrtc/blob/master/sdk/android/api/org/webrtc/ScreenCapturerAndroid.java

typealias DetectionResults = List<EfficientdetTfliteLite2DetectionMetadataV1.DetectionResult>

class RecorderService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "resultcode"
        const val EXTRA_DATA = "data"
        const val EXTRA_STOP = "stop"
        const val EXTRA_START_STOP_BLOCKING = "start_stop_blocking"

        const val SERVICE_NOTIFICATION_CHANNEL_ID = "RecorderNotificationChannelId"
        const val SERVICE_NOTIFICATION_ID = 12

        //Tensorflow process image width and height
        const val OBJECT_DETECTION_REQUIRE_IMAGE_SIZE = 448
    }

    private var familiarityScore: Float = 0.2f
    private var selectedLabels: List<String> = emptyList()
    private val binder = RecorderBinder()

    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()

    var data: Intent? = null
    var resultCode: Int? = null

    private var screenDensity: Int = 0
    private lateinit var projectionManager: MediaProjectionManager
    private val objectDetectionScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var imageView: ImageView
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var model: EfficientdetTfliteLite2DetectionMetadataV1
    private lateinit var imageReader: ImageReader

    private var serviceLooper: Looper? = null
    private lateinit var serviceHandler: ServiceHandler
    private lateinit var mainHandler: Handler

    private var displayWidth: Int = 0
    private var displayHeight: Int = 0

    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var mediaProjection: MediaProjection

    private lateinit var windowManager: WindowManager

    private var orientationChangeCallback: OrientationChangeCallback? = null

    private inner class ServiceHandler(looper: Looper) : Handler(looper)

    private inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            val image = reader?.acquireLatestImage()
            try {
                image?.let {

                    val planes = image.planes
                    val buffer = planes[0].buffer.rewind()
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding: Int = rowStride - pixelStride * image.width

                    // create bitmap
                    val bitmap = Bitmap.createBitmap(
                        image.width + rowPadding / pixelStride, image.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    /*val byteArrayOutputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,byteArrayOutputStream)
                    val base64 = Base64.encodeToString(byteArrayOutputStream.toByteArray(),Base64.DEFAULT)*/

                    // Creates inputs for reference.
                    var tensorImage = TensorImage.fromBitmap(bitmap)
                    tensorImage = imageProcessor.process(tensorImage)
                    // Runs model inference and gets result.
                    val outputs = model.process(tensorImage)

                    createRectangles(bitmap, outputs.detectionResultList)

                    bitmap.recycle()

                }
            } catch (e: Exception) {
                e.printStackTrace()
                image?.close()
            } finally {
                image?.close()
            }

        }

    }

    override fun onCreate() {
        super.onCreate()

        serviceScope.launch {
            dataStore.data.collectLatest {preferences ->
                selectedLabels = preferences[SELECTED_LABELS]?.split(",") ?: emptyList()
                familiarityScore = preferences[FAMILIARITY_VALUE] ?: 0.2f
            }
        }

        windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager

        startForegroundService()

        initOrientationChangeCallback()

        HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()

            serviceLooper = looper
            serviceHandler = ServiceHandler(looper)
        }
        mainHandler = Handler(mainLooper)

        initWindowManagerOverlayLayout()

        model = EfficientdetTfliteLite2DetectionMetadataV1.newInstance(this)

        imageProcessor =
            ImageProcessor.Builder().add(
                ResizeOp(
                    OBJECT_DETECTION_REQUIRE_IMAGE_SIZE,
                    OBJECT_DETECTION_REQUIRE_IMAGE_SIZE,
                    ResizeOp.ResizeMethod.BILINEAR
                )
            ).build()


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0)
        data = intent?.parcelable(EXTRA_DATA)

        val isStop = intent?.getBooleanExtra(EXTRA_STOP, false) ?: false
        val startStopBlocking = intent?.getBooleanExtra(EXTRA_START_STOP_BLOCKING,false)
        if(startStopBlocking == true){
            if(_recording.value) stopRecording() else startRecording()
        }else if (isStop) {
            stopSelf()
        } else {
            check(!(resultCode != 0 && data == null)) {
                throw IllegalStateException("Result code or data missing")
            }

            startRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        setRecordingState(false)
        resultCode = null
        data = null
        objectDetectionScope.cancel()
        serviceScope.cancel()
        orientationChangeCallback?.disable()
        windowManager.removeView(imageView)
        mediaProjection.stop()
        virtualDisplay.release()
        imageReader.close()
        mainHandler.postDelayed({
            model.close()
        }, 500)

    }

    private fun setRecordingState(state: Boolean){
        serviceScope.launch {
            _recording.emit(state)
        }
    }

    private fun initImageReader() {
        if (this::imageReader.isInitialized) {
            imageReader.close()
        }
        imageReader = ImageReader.newInstance(
            displayWidth,
            displayHeight,
            PixelFormat.RGBA_8888,
            2
        )
        imageReader.setOnImageAvailableListener(
            ImageAvailableListener(),
            Handler(serviceLooper!!)
        )
    }

    private fun setDisplaySizeAndDensity() {
        screenDensity = applicationContext.resources.configuration.densityDpi

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayWidth = windowManager.currentWindowMetrics.bounds.width()
            displayHeight = windowManager.currentWindowMetrics.bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            displayWidth = displayMetrics.widthPixels
            displayHeight = displayMetrics.heightPixels
        }
    }

    fun startRecording() {

        projectionManager =
            applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setDisplaySizeAndDensity()

        initImageReader()

        // Another way to read imageReader Surface and detect object
        //processScreenCapture()

        createVirtualDisplay()

        orientationChangeCallback?.enable()

        setRecordingState(true)

        val notification = getNotification(description = getString(R.string.description_notification_screen_recording))

        with(NotificationManagerCompat.from(this)){
            notify(SERVICE_NOTIFICATION_ID,notification)
        }

    }

    private fun createVirtualDisplay(){
        if(!this::virtualDisplay.isInitialized){
            mediaProjection = projectionManager.getMediaProjection(resultCode!!, data!!)
            mediaProjection.registerCallback(object : MediaProjection.Callback() {

            }, Handler(serviceLooper!!))

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "RecorderCat",
                displayWidth,
                displayHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                Handler(serviceLooper!!)
            )
            return
        }
        virtualDisplay.surface = imageReader.surface

    }

    fun stopRecording() {
        orientationChangeCallback?.disable()
        imageView.setImageBitmap(null)
        virtualDisplay.surface = null
        imageReader.close()

        setRecordingState(false)

        val notification = getNotification(description = getString(R.string.description_notification_block_paused))

        with(NotificationManagerCompat.from(this)){
            notify(SERVICE_NOTIFICATION_ID,notification)
        }
    }

    private fun processScreenCapture() {
        objectDetectionScope.launch {
            while (true) {
                val bitmap =
                    Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888)

                PixelCopy.request(
                    imageReader.surface,
                    bitmap,
                    object : PixelCopy.OnPixelCopyFinishedListener,
                        Consumer<PixelCopy.Result> {
                        override fun onPixelCopyFinished(copyResult: Int) {
                            var tensorImage = TensorImage.fromBitmap(bitmap)
                            tensorImage = imageProcessor.process(tensorImage)
                            // Runs model inference and gets result.
                            val outputs = model.process(tensorImage)

                            mainHandler.post {

                                createRectangles(bitmap, outputs.detectionResultList)
                            }

                        }

                        override fun accept(t: PixelCopy.Result) = Unit

                    },
                    mainHandler
                )
                delay(400)
            }
        }
    }

    private fun getNotification(title: String = getString(R.string.app_name),description: String): Notification{

        val stopServiceIntent = Intent(applicationContext,RecorderService::class.java).also {
            it.putExtra(EXTRA_STOP,true)
        }
        val stopServicePendingIntent = PendingIntent.getService(applicationContext,0,stopServiceIntent,PendingIntent.FLAG_IMMUTABLE)

        val stopServiceAction = NotificationCompat.Action.Builder(0,getString(R.string.label_stop_service),stopServicePendingIntent)
            .build()

        val startStopBlockingIntent = Intent(applicationContext,RecorderService::class.java).also {
            it.putExtra(EXTRA_START_STOP_BLOCKING,true)
        }

        val startStopBlockingPendingIntent = PendingIntent.getService(applicationContext,1,startStopBlockingIntent,PendingIntent.FLAG_IMMUTABLE)

        val label = if(_recording.value) getString(R.string.label_stop_blocking) else getString(R.string.label_start_blocking)
        val startStopPreventAction = NotificationCompat.Action.Builder(0,label,startStopBlockingPendingIntent)
            .build()


        val notificationBuilder = NotificationCompat.Builder(this, SERVICE_NOTIFICATION_CHANNEL_ID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(R.drawable.ic_worried_cat)
            .setColor(primaryLight.toArgb())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if(this::virtualDisplay.isInitialized){
            notificationBuilder.addAction(startStopPreventAction)
        }

        notificationBuilder.addAction(stopServiceAction)

        return notificationBuilder.build()
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val notification = getNotification(description = getString(R.string.description_notification_block_unwanted_contents))

        ServiceCompat.startForeground(
            this,
            SERVICE_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        )
    }

    private fun createNotificationChannel() {
        val notificationChannel =
            NotificationChannel(
                SERVICE_NOTIFICATION_CHANNEL_ID,
                getString(R.string.description_service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
    }


    private fun initWindowManagerOverlayLayout() {

        mainHandler.post {

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,  // Display it on top of other application windows
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // Don't let it grab the input focus
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  // Make the underlying application window visible
                // through any transparent parts
                PixelFormat.TRANSLUCENT
            )

            imageView = ImageView(this)

            windowManager.addView(imageView, params)
        }

    }

    private fun initOrientationChangeCallback() {
        orientationChangeCallback = OrientationChangeCallback(this) {
            serviceHandler.postDelayed(
                {
                    if (this::virtualDisplay.isInitialized && virtualDisplay.surface != null) {

                        setDisplaySizeAndDensity()

                        initImageReader()

                        virtualDisplay.surface = imageReader.surface
                        virtualDisplay.resize(displayWidth, displayHeight, screenDensity)
                    }
                }, 800
            )

        }
    }

    private fun createRectangles(
        bitmap: Bitmap,
        detectionResults: DetectionResults
    ) {
        val selectedObjects = detectionResults.filter { it.categoryAsString in selectedLabels }

        val mutableBitmap =
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        val paint = Paint().apply {
            color = primaryLight.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 6f
            textSize = 40f
        }
        imageView.background = ColorDrawable(
            ContextCompat.getColor(
                this,
                android.R.color.transparent
            )
        )

        val canvas = Canvas(mutableBitmap)

        selectedObjects.forEach { detection ->
            if (detection.scoreAsFloat > familiarityScore) {
                val rectF = detection.locationAsRectF

                val left =
                    (rectF.left * bitmap.width) / OBJECT_DETECTION_REQUIRE_IMAGE_SIZE
                val top = (rectF.top * bitmap.height) / OBJECT_DETECTION_REQUIRE_IMAGE_SIZE
                val right = (rectF.right * bitmap.width) / OBJECT_DETECTION_REQUIRE_IMAGE_SIZE
                val bottom = (rectF.bottom * bitmap.height) / OBJECT_DETECTION_REQUIRE_IMAGE_SIZE

                val areaOfObject = (right - left) * (bottom - top)
                val areaOfScreen = bitmap.width * bitmap.height

                val percentageOfArea = (1f * areaOfObject) / areaOfScreen
                if (percentageOfArea <= 0.8f) {
                    // Content Rectangle
                    canvas.drawRoundRect(
                        left,
                        top,
                        right,
                        bottom,
                        50f, 50f,
                        paint
                    )
                    //Cross Lines
                    canvas.drawLine(
                        left + 15f, top + 15f, right - 15f, bottom - 15f, paint
                    )
                    canvas.drawLine(
                        right - 15f, top + 15f, left + 15f, bottom - 15f, paint
                    )
                    // Cat Image
                    /*val drawable = ContextCompat.getDrawable(this,R.drawable.ic_weary_cat)
                    drawable?.setBounds(left.toInt(),top.toInt(),right.toInt(),bottom.toInt())
                    drawable?.draw(canvas)*/
                    //paint.color = Color.BLACK
                    /*canvas.drawText(
                        "${detection.categoryAsString} - ${detection.scoreAsFloat}",
                        left,
                        top,
                        paint
                    )*/
                }

            }
        }
        imageView.setImageBitmap(mutableBitmap)

    }


    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    inner class RecorderBinder: Binder(){
        fun getService(): RecorderService = this@RecorderService
    }
}