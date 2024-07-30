package com.aoguzhan.worriedcat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.aoguzhan.compose.ScreenCaptureCatTheme
import com.aoguzhan.worriedcat.presentation.home.HomeScreen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private var recorderService = mutableStateOf<RecorderService?>(null)
    private var bound: Boolean = false

    private val recorderConnection = object : ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecorderService.RecorderBinder
            recorderService.value = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {result ->
        if(result.resultCode == RESULT_OK){
            startRecordingService(result.resultCode,result.data)
            serviceStartFlag = 1
        }
    }

    private var serviceStartFlag = 0

    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }


        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            ScreenCaptureCatTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    var recording by remember {
                        mutableStateOf(false)
                    }


                    LaunchedEffect(recorderService.value) {
                        recorderService.value?.let{ service ->
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                                service.recording.collectLatest{
                                    recording = it
                                }
                            }
                        }
                    }

                    HomeScreen(
                        recording = recording,
                        onRecordClick = {
                            if(!recording) startRecording() else recorderService.value?.stopRecording()
                        }
                    )

                }
            }
        }
    }

    private fun startRecording() {

        if(!Settings.canDrawOverlays(this)){
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package: $packageName"))
            startActivity(intent)
            return
        }


        if(recorderService.value?.resultCode != null){
            recorderService.value?.startRecording()
            return
        }

        resultLauncher.launch(projectionManager.createScreenCaptureIntent())

    }
    private fun startRecordingService(resultCode: Int, data: Intent?){
        Intent(this,RecorderService::class.java).apply {
            putExtra(RecorderService.EXTRA_RESULT_CODE,resultCode)
            putExtra(RecorderService.EXTRA_DATA,data)

            startService(this)

            bindService(this,recorderConnection,0)
        }

    }

    override fun onStart() {
        super.onStart()
        Intent(this, RecorderService::class.java).also { intent ->
            bindService(intent, recorderConnection, 0)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(recorderConnection)
        bound = false
    }
}