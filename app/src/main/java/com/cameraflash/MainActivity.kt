package com.cameraflash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraMetadata.FLASH_MODE_OFF
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Range
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), View.OnClickListener, SetValues {
    var previousPosISO = -1
    lateinit var previousModelISO: ModelClass


    var previousPosShutter = -1
    lateinit var previousModelShutter: ModelClass

    var range: Range<Int>? = null

    var listISO = ArrayList<ModelClass>()
    var shutterList = ArrayList<ModelClass>()
    lateinit var adapter: AdapterClass


    companion object {
        private const val TAG = "MainActivity"
        private val ORIENTATIONS = SparseIntArray()
        private const val REQUEST_CAMERA_PERMISSION = 200

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    var cameraId: String = ""
    protected var cameraDevice: CameraDevice? = null
    protected var cameraCaptureSessions: CameraCaptureSession? = null
    protected var captureRequest: CaptureRequest? = null
    protected var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimension: Size? = null
    private var imageReader: ImageReader? = null
    private val file: File? = null
    private val mFlashSupported = false
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        addShutterList()

//        textureView = findViewById<View>(R.id.texture) as TextureView
//        assert(textureView != null)
//        textureView!!.surfaceTextureListener = textureListener
//        takePictureButton = findViewById<View>(R.id.btn_takepicture) as Button
//        assert(takePictureButton != null)
//        takePictureButton!!.setOnClickListener { takePicture() }
    }

    private fun addShutterList() {
        shutterList.add(ModelClass("1/1000s", false))
        shutterList.add(ModelClass("1/500s", false))
        shutterList.add(ModelClass("1/250s", false))
        shutterList.add(ModelClass("1/125s", false))
        shutterList.add(ModelClass("1/60s", false))
        shutterList.add(ModelClass("1/30s", false))
        shutterList.add(ModelClass("1/15s", false))
        shutterList.add(ModelClass("1/8s", false))
        shutterList.add(ModelClass("1/4s", false))
        shutterList.add(ModelClass("1/2s", false))
        shutterList.add(ModelClass("1s", false))
        shutterList.add(ModelClass("2s", false))
        shutterList.add(ModelClass("4s", false))
        shutterList.add(ModelClass("8s", false))
        shutterList.add(ModelClass("16s", false))
        shutterList.add(ModelClass("32s", false))
    }

    private fun initViews() {
        previousModelShutter = ModelClass("1/250", true)
        previousModelISO = ModelClass("500", true)

        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        tvISO.setOnClickListener(this)
        tvShutter.setOnClickListener(this)
        btnPicture.setOnClickListener(this)
    }

    var textureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            //open your camera here
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            // Transform you image captured size according to the surface width and height
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice!!.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice!!.close()
            cameraDevice = null
        }
    }
    val captureCallbackListener: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            Toast.makeText(this@MainActivity, "Saved:$file", Toast.LENGTH_SHORT).show()
            createCameraPreview()
        }
    }

    protected fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    protected fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    protected fun takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null")
            return
        }
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = manager.getCameraCharacteristics(cameraDevice!!.id)
            range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: null
            if (range != null)
                getISORange(range)


            var jpegSizes: Array<Size>? = null
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG)
            }
            var width = 640
            var height = 480
            if (jpegSizes != null && 0 < jpegSizes.size) {
                width = jpegSizes[0].width
                height = jpegSizes[0].height
            }

            val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurfaces: MutableList<Surface> = ArrayList(2)
            outputSurfaces.add(reader.surface)
            outputSurfaces.add(Surface(textureView!!.surfaceTexture))
            val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureBuilder.set(CaptureRequest.FLASH_MODE, FLASH_MODE_OFF);
            // Orientation
            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS[rotation])

            val captureBuilderTwo = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilderTwo.addTarget(reader.surface)
            captureBuilderTwo.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureBuilderTwo.set(CaptureRequest.FLASH_MODE, FLASH_MODE_OFF);
            captureBuilderTwo.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS[rotation])

            val time = System.currentTimeMillis()
            val file = File(Environment.getExternalStorageDirectory().toString() + "/"+time+"pic.jpg")

            val time1 = System.currentTimeMillis()
            Log.e(TAG,time1.toString())


            val readerListener: ImageReader.OnImageAvailableListener = object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(reader: ImageReader) {
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.capacity())
                        buffer[bytes]
                        save(bytes)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        image?.close()
                    }
                }

                @Throws(IOException::class)
                private fun save(bytes: ByteArray) {
                    var output: OutputStream? = null
                    try {
                        output = FileOutputStream(file)
                        output.write(bytes)
                    } finally {
                        output?.close()
                    }
                }
            }
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler)
            val captureListener: CaptureCallback = object : CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    Toast.makeText(this@MainActivity, "Saved:$file", Toast.LENGTH_SHORT).show()
                    createCameraPreview()
                    Log.e(TAG,"onCaptureCompleted-> "+System.currentTimeMillis());
                }

                override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {
                    super.onCaptureProgressed(session, request, partialResult)
                    Log.e(TAG,"onCaptureProgressed-> "+System.currentTimeMillis());
                }

                override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    Log.e(TAG,"onCaptureStarted-> "+System.currentTimeMillis());

                }


            }
            cameraDevice!!.createCaptureSession(outputSurfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler)
//                        Handler().postDelayed(Runnable {
//                            startTcaptureTwo(session,captureBuilderTwo);
//                        },3)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    fun startTcaptureTwo(session: CameraCaptureSession, builder: CaptureRequest.Builder) {

        val captureListenerTwo: CaptureCallback = object : CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                Log.e(TAG,"onCaptureCompletedTwo-> "+System.currentTimeMillis());
                Toast.makeText(this@MainActivity, "Saved:$file", Toast.LENGTH_SHORT).show()
                createCameraPreview()

            }

            override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                Log.e(TAG,"onCaptureStartedTwo-> "+System.currentTimeMillis())
            }

        }

        try {
            Log.e(TAG,"onConfigured-> "+System.currentTimeMillis())
            session.capture(builder.build(), captureListenerTwo, mBackgroundHandler)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }


    private fun getISORange(range: Range<Int>?) {
        var minRange = range!!.lower
        val maxRange = range!!.upper
        listISO.add(ModelClass(minRange.toString(), false))
        minRange += 200
        while (minRange <= maxRange) {
            listISO.add(ModelClass(minRange.toString(), false))
            minRange += 200

        }
        listISO.add(ModelClass(maxRange.toString(), false))


    }


    protected fun createCameraPreview() {
        try {
            val texture = textureView!!.surfaceTexture!!
            texture.setDefaultBufferSize(imageDimension!!.width, imageDimension!!.height)
            val surface = Surface(texture)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
            captureRequestBuilder!!.set(CaptureRequest.SENSOR_EXPOSURE_TIME,10000000) //previousModelShutter.value.toLong());
            captureRequestBuilder!!.set(CaptureRequest.SENSOR_SENSITIVITY, previousModelISO.value.toInt());
            captureRequestBuilder!!.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_TORCH)
            captureRequestBuilder!!.addTarget(surface)
            val mCameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                val torchCallback: CameraManager.TorchCallback = object : CameraManager.TorchCallback() {
//                    override
//                    fun onTorchModeUnavailable(cameraId: String?) {
//                        super.onTorchModeUnavailable(cameraId)
//                    }
//                    override
//                    fun onTorchModeChanged(cameraId: String?, enabled: Boolean) {
//                        super.onTorchModeChanged(cameraId, enabled)
//
//                    }
//                }
//                val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
//                manager.registerTorchCallback(torchCallback, null) // (callback, handler)
//
//            } else {
//                TODO("VERSION.SDK_INT < M")
//            }
//            mCameraManager.setTorchMode(cameraId, false)
            cameraDevice!!.createCaptureSession(Arrays.asList(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession
                    updatePreview()
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    Toast.makeText(this@MainActivity, "Configuration change", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.e(TAG, "is camera open")
        try {
            cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: null
            if (range != null)
                getISORange(range)

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CAMERA_PERMISSION)
                return
            }
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.e(TAG, "openCamera X")
    }

    protected fun updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return")
        }
        captureRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        try {
            cameraCaptureSessions!!.setRepeatingRequest(captureRequestBuilder!!.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        if (null != cameraDevice) {
            cameraDevice!!.close()
            cameraDevice = null
        }
        if (null != imageReader) {
            imageReader!!.close()
            imageReader = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(this@MainActivity, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume")
        startBackgroundThread()
        if (textureView!!.isAvailable) {
            openCamera()
        } else {
            textureView!!.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Log.e(TAG, "onPause")
        //closeCamera();
        stopBackgroundThread()
        super.onPause()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.tvISO -> {
                adapter = AdapterClass(0, this, listISO, this)
                rv.adapter = adapter

            }
            R.id.tvShutter -> {
                adapter = AdapterClass(1, this, shutterList, this)
                rv.adapter = adapter
            }
            R.id.btnPicture -> {
                takePicture()
            }
        }
    }

    override fun returnValue(type: Int, value: String, model: ModelClass, pos: Int) {
        if (type == 0) {
            if (previousPosISO != -1) {
                previousModelISO.isSelected = false
                listISO.set(previousPosISO, previousModelISO)


                previousModelISO = model
                previousPosISO = pos


            } else {

                previousModelISO = model
                previousPosISO = pos
            }




            model.isSelected = true
            listISO.set(pos, model)
        } else {

            if (previousPosShutter != -1) {
                previousModelShutter.isSelected = false
                shutterList.set(previousPosShutter, previousModelShutter)


                previousModelShutter = model
                previousPosShutter = pos


            } else {
                previousModelShutter = model
                previousPosShutter = pos
            }


            model.isSelected = true
            shutterList.set(pos, model)
        }
        rv.post(Runnable { adapter.notifyDataSetChanged() })


    }


}

interface SetValues {

    fun returnValue(type: Int, value: String, model: ModelClass, pos: Int)
}