package com.demo.camera2demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.tbruyelle.rxpermissions2.RxPermissions
import com.trello.lifecycle2.android.lifecycle.AndroidLifecycle
import com.trello.rxlifecycle2.LifecycleTransformer
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableObserver
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 初始化流程:
 * 1.初始化动态授权,这是基本操作
 * 2.初始化一个子线程的Handler,Camera2的操作可以放在主线程也可以放在子线程.
 * 按例一般都是子线程里,但是Camera2只需要我们提供一个子线程的Handler就行了.
 * 3.初始化ImageReader,这个没有初始化顺序要求,并且它有数据回调接口,
 * 接口回调的图片数据我们直接保存到内部存储空间,所以提前初始化提供给后续使用.
 * 4.初始化TextureView,添加TextureView的接口回调.
 * 5.在TextureView的接口回调里回调启用成功方法后,我们开始初始化相机管理类initCameraManager
 * 6.然后继续初始化CameraDevice.StateCallback 摄像头设备状态接口回调类,先初始化提供给后续使用.
 * (在这个接口类的开启相机的回调方法里,我们需要实现创建预览图像请求配置和创建获取数据会话)
 * 7.继续初始化CameraCaptureSession.StateCallback 摄像头获取数据会话类的状态接口回调类,
 * 先初始化提供给后续使用.(在这个接口类的配置成功回调方法里,我们需要实现预览图像或者实现拍照)
 * 8.继续初始化CameraCaptureSession.CaptureCallback
 * 摄像头获取数据会话类的获取接口回调类,先初始化提供给后续使用.(啥都不干)
 * 9.判断摄像头前后,选择对应id
 * 10.打开指定id的摄像头
 * 11.实现拍照
 */
class MainActivity : AppCompatActivity() {
    private val rxPermissions: RxPermissions = RxPermissions(this)
    //子线程
    private lateinit var mHandlerThread: HandlerThread
    //Handler 消息处理机制
    private lateinit var mChildHandler: Handler
    //相机管理类,用于检测系统相机获取相机id
    private lateinit var mCameraManager: CameraManager
    //Camera设备类
    private lateinit var mCameraDevice: CameraDevice
    //获取的会话类状态回调
    private lateinit var mSessionStateCallback: CameraCaptureSession.StateCallback
    //获取会话类的获取数据回调
    private lateinit var mSessionCaptureCallback: CameraCaptureSession.CaptureCallback
    //获取数据请求配置类
    private lateinit var mCaptureRequest: CaptureRequest.Builder
    //摄像头状态回调
    private lateinit var mStateCallback: CameraDevice.StateCallback
    //获取数据会话类
    private lateinit var mCameraCaptureSession: CameraCaptureSession
    //照片读取器
    private lateinit var mImageReader: ImageReader
    private lateinit var mSurface: Surface
    private lateinit var mSurfaceTexture: SurfaceTexture
    private lateinit var mCurrentCameraId: String

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //1.初始化动态授权,这是基本操作
        rxPermissions
            .request(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .subscribe { granted ->
                if (granted) {
                    //2.初始化一个子线程的Handler,Camera2的操作可以放在主线程也可以放在子线程.按例一般都是子线程里,但是Camera2只需要我们提供一个子线程的Handler就行了.
                    initChildThread()
                    //3.初始化ImageReader,这个没有初始化顺序要求,并且它有数据回调接口,接口回调的图片数据我们直接保存到内部存储空间,所以提前初始化提供给后续使用.
                    initImageReader()
                    //4.初始化TextureView,添加TextureView的接口回调.
                    initTextureView()
                } else {
                    finish()
                }
            }
    }

    /**
     * 2.初始化一个子线程的Handler,Camera2的操作可以放在主线程也可以放在子线程.按例一般都是子线程里,但是Camera2只需要我们提供一个子线程的Handler就行了.
     */
    private fun initChildThread() {
        mHandlerThread = HandlerThread("camera2")
        mHandlerThread.start()
        mChildHandler = Handler(mHandlerThread.looper)
    }

    /**
     * 3.初始化ImageReader,这个没有初始化顺序要求,并且它有数据回调接口,接口回调的图片数据我们直接保存到内部存储空间,所以提前初始化提供给后续使用.
     */
    private fun initImageReader() {
        //创建图片读取器,参数为分辨率宽度和高度/图片格式/需要缓存几张图片,我这里写的2意思是获取2张照片
        mImageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 2)
        mImageReader.setOnImageAvailableListener({ reader ->
            //image.acquireLatestImage()//从ImageReader的队列中获取最新的image,删除旧的
            //image.acquireNextImage()//从ImageReader的队列中获取下一个图像,如果返回null没有新图像可用
            reader.acquireNextImage()?.let {
                val byteBuffer = it.planes[0].buffer
                val byteArray = ByteArray(byteBuffer.remaining())
                byteBuffer.get(byteArray)
                BitmapUtils.toBitmap(byteArray, { bitmap ->
                    runOnUiThread {
                        ivShow.setImageBitmap(bitmap)
                        timeRunning = false
                    }
                }, { msg ->
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                })
                reader.close()
            }
        }, mChildHandler)
    }

    /**
     * 4.初始化TextureView,添加TextureView的接口回调.
     */
    private fun initTextureView() {
        mTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            //TextureView 启用成功
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                //5.在TextureView的接口回调里回调启用成功方法后,我们开始初始化相机管理类initCameraManager
                initCameraManager()
                //6.然后继续初始化CameraDevice.StateCallback 摄像头设备状态接口回调类,先初始化提供给后续使用.(在这个接口类的开启相机的回调方法里,我们需要实现创建预览图像请求配置和创建获取数据会话)
                initCameraCallback()
                //7.继续初始化CameraCaptureSession.StateCallback 摄像头获取数据会话类的状态接口回调类,先初始化提供给后续使用.(在这个接口类的配置成功回调方法里,我们需要实现预览图像或者实现拍照)
                initCameraCaptureSessionStateCallback()
                //8.继续初始化CameraCaptureSession.CaptureCallback 摄像头获取数据会话类的获取接口回调类,先初始化提供给后续使用.(啥都不干)
                initCameraCaptureSessionCaptureCallback()
                //9.判断摄像头前后,选择对应id
                selectCamera()
                //10.打开指定id的摄像头
                openCamera()
            }

            //SurfaceTexture变化
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            }

            //SurfaceTexture的销毁 这里返回true则是交由系统执行释放，如果是false则需要自己调用surface.release();
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }
        }
    }

    /**
     * 5.在TextureView的接口回调里回调启用成功方法后,我们开始初始化相机管理类initCameraManager
     */
    private fun initCameraManager() {
        mCameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /**
     * 6.然后继续初始化CameraDevice.StateCallback 摄像头设备状态接口回调类,先初始化提供给后续使用.(在这个接口类的开启相机的回调方法里,我们需要实现创建预览图像请求配置和创建获取数据会话)
     */
    private fun initCameraCallback() {
        mStateCallback = object : CameraDevice.StateCallback() {
            //摄像头打开时
            override fun onOpened(camera: CameraDevice) {
                Log.e(TAG, "相机开启")
                mCameraDevice = camera
                try {
                    //surfaceTexture需要手动释放
                    mSurfaceTexture = mTextureView.surfaceTexture
                    val matchingSize = matchingSize()
                    //设置预览的图像尺寸
                    mSurfaceTexture.setDefaultBufferSize(matchingSize.width, matchingSize.height)
                    //surface最好在销毁的时候要释放,surface.release()
                    mSurface = Surface(mSurfaceTexture)
                    //CaptureRequest可以完全自定义拍摄参数,但是需要配置的参数太多了,所以Camera2提供了一些快速配置的参数,如下:
                    //TEMPLATE_PREVIEW ：预览
                    //TEMPLATE_RECORD：拍摄视频
                    //TEMPLATE_STILL_CAPTURE：拍照
                    //TEMPLATE_VIDEO_SNAPSHOT：创建视视频录制时截屏的请求
                    //TEMPLATE_ZERO_SHUTTER_LAG：创建一个适用于零快门延迟的请求。在不影响预览帧率的情况下最大化图像质量。
                    //TEMPLATE_MANUAL：创建一个基本捕获请求，这种请求中所有的自动控制都是禁用的(自动曝光，自动白平衡、自动焦点)。

                    //创建预览请求
                    mCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    //添加surface实际使用中这个surface最好是全局变量 在onDestroy的时候mCaptureRequest.removeTarget(mSurface);清除,否则会内存泄露
                    mCaptureRequest.addTarget(mSurface)

                    //闪光灯
                    mCaptureRequest.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    )

                    //自动对焦
                    mCaptureRequest.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    //开启人脸检测
                    mCaptureRequest.set(
                        CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                        CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE
                    )
                    /**
                     * 创建获取会话
                     * 这里会有一个容易忘记的坑,那就是Arrays.asList(surface, mImageReader.getSurface())这个方法
                     * 这个方法需要你导入后面需要操作功能的所有surface,比如预览/拍照如果你2个都要操作那就要导入2个
                     * 否则后续操作没有添加的那个功能就报错surface没有准备好,这也是我为什么先初始化ImageReader的原因,因为在这里就可以拿到ImageReader的surface了
                     */
                    mCameraDevice.createCaptureSession(
                        listOf(mSurface, mImageReader.surface),
                        mSessionStateCallback,
                        mChildHandler
                    )
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            //摄像头断开时
            override fun onDisconnected(camera: CameraDevice) {

            }

            //出现异常情况时
            override fun onError(camera: CameraDevice, error: Int) {

            }

            //摄像头关闭时
            override fun onClosed(camera: CameraDevice) {
                super.onClosed(camera)
            }
        }
    }

    /**
     * 7.继续初始化CameraCaptureSession.StateCallback 摄像头获取数据会话类的状态接口回调类,先初始化提供给后续使用.(在这个接口类的配置成功回调方法里,我们需要实现预览图像或者实现拍照)
     */
    private fun initCameraCaptureSessionStateCallback() {
        mSessionStateCallback = object : CameraCaptureSession.StateCallback() {
            //摄像头完成配置，可以处理Capture请求了。
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    mCameraCaptureSession = session
                    //注意这里使用的是 setRepeatingRequest() 请求通过此捕获会话无休止地重复捕获图像。用它来一直请求预览图像
                    mCameraCaptureSession.setRepeatingRequest(
                        mCaptureRequest.build(),
                        mSessionCaptureCallback,
                        mChildHandler
                    )
                    //停止重复   取消任何正在进行的重复捕获集
                    //mCameraCaptureSession.stopRepeating()
                    //终止获取   尽可能快地放弃当前挂起和正在进行的所有捕获。请只在销毁activity的时候调用它
                    //mCameraCaptureSession.abortCaptures()
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            //摄像头配置失败
            override fun onConfigureFailed(session: CameraCaptureSession) {

            }
        }
    }

    /**
     * 8.继续初始化CameraCaptureSession.CaptureCallback 摄像头获取数据会话类的获取接口回调类,先初始化提供给后续使用.(啥都不干)
     */
    private fun initCameraCaptureSessionCaptureCallback() {
        mSessionCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
            }

            override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
            ) {
                super.onCaptureProgressed(session, request, partialResult)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                //Log.e(TAG, "onCaptureCompleted: 触发接收数据");
                //Size size = request.get(CaptureRequest.JPEG_THUMBNAIL_SIZE);
                //人脸检测
                checkFace(result)
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure
            ) {
                super.onCaptureFailed(session, request, failure)
            }

            override fun onCaptureSequenceCompleted(session: CameraCaptureSession, sequenceId: Int, frameNumber: Long) {
                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber)
            }

            override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
                super.onCaptureSequenceAborted(session, sequenceId)
            }

            override fun onCaptureBufferLost(
                session: CameraCaptureSession,
                request: CaptureRequest,
                target: Surface,
                frameNumber: Long
            ) {
                super.onCaptureBufferLost(session, request, target, frameNumber)
            }
        }
    }

    var timeRunning = false
    private fun checkFace(result: TotalCaptureResult) {
        val faces = result.get(CaptureResult.STATISTICS_FACES)
        if (!faces.isNullOrEmpty()) {
            if (!timeRunning) {
                Observable.interval(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .take(4)
                    .compose(bindLifecycle(this))
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : DisposableObserver<Long>() {
                        override fun onNext(t: Long) {
                            timeRunning = true
                            tvTimer.text = (3 - t).toInt().toString()
                            tvHint.setTextColor(Color.BLUE)
                            tvHint.text = "检测到人脸,请保持姿势"
                        }

                        override fun onError(e: Throwable) {
                            timeRunning = false
                            tvTimer.text = ""
                            tvHint.text = ""
                        }

                        override fun onComplete() {
                            tvTimer.text = ""
                            tvHint.text = ""
                            //11.实现拍照
                            takePicture()
                        }
                    })
            } else {
                return
            }
        } else {
            if (!timeRunning) {
                runOnUiThread {
                    tvTimer.text = ""
                    tvHint.setTextColor(Color.RED)
                    tvHint.text = "未检测到人脸"
                }
            }
        }
    }

    /**
     * 9.判断摄像头前后,选择对应id
     */
    private fun selectCamera() {
        try {
            //获取摄像头id列表
            val cameraIdList = mCameraManager.cameraIdList
            if (cameraIdList.isEmpty()) {
                return
            }
            for (cameraId in cameraIdList) {
                Log.e(TAG, "selectCamera: cameraId=$cameraId")
                //获取相机特征,包含前后摄像头信息，分辨率等
                val cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId)
                //获取这个摄像头的面向
                val facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                //CameraCharacteristics.LENS_FACING_BACK 后摄像头
                //CameraCharacteristics.LENS_FACING_FRONT 前摄像头
                //CameraCharacteristics.LENS_FACING_EXTERNAL 外部摄像头,比如OTG插入的摄像头
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    mCurrentCameraId = cameraId
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * 10.打开指定id的摄像头
     */
    @SuppressLint("NewApi")
    private fun openCamera() {
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                mCameraManager.openCamera(mCurrentCameraId, mStateCallback, mChildHandler)
                return
            } else {
                Toast.makeText(this, "没有授权", Toast.LENGTH_SHORT).show()
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * 11.实现拍照
     */
    private fun takePicture() {
        //停止重复   取消任何正在进行的重复捕获集 在这里就是停止画面预览
        mCameraCaptureSession.stopRepeating()
        /*   终止获取 尽可能快地放弃当前挂起和正在进行的所有捕获。
         *   mCameraCaptureSession.abortCaptures();
         *   这里有一个坑,其实这个并不能随便调用(我是看到别的demo这么使用,但是其实是错误的,所以就在这里备注这个坑).
         *   最好只在Activity里的onDestroy调用它,终止获取是耗时操作,需要一定时间重新打开会话通道.
         *   在这个demo里我并没有恢复预览,如果你调用了这个方法关闭了会话又拍照后恢复图像预览,会话就会频繁的开关,
         *   导致拍照图片在处理耗时缓存时你又关闭了会话.导致照片缓存不完整并且失败.
         *   所以切记不要随便使用这个方法,会话开启后并不需要关闭刷新.后续其他拍照/预览/录制视频直接操作这个会话即可
         */
        try {
            var captureRequestBuilder: CaptureRequest.Builder =
                mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            //自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            //自动爆光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            //获取手机方向,如果你的app有提供横屏和竖屏,那么就需要下面的方法来控制照片为竖立状态
            //int rotation = getWindowManager().getDefaultDisplay().getRotation()
            //Log.e(TAG, "takePicture: 手机方向="+rotation)
            //Log.e(TAG, "takePicture: 照片方向="+ORIENTATIONS.get(rotation))
            //我的项目不需要,直接写死270度 将照片竖立
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270)
            val surface = mImageReader.surface
            captureRequestBuilder.addTarget(surface)
            //获取拍照
            mCameraCaptureSession.capture(captureRequestBuilder.build(), null, mChildHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * 获取匹配的大小
     *
     * @return
     */
    private fun matchingSize(): Size {
        var selectSize: Size? = null
        var selectProportion = 0f
        try {
            val viewProportion = mTextureView.width.toFloat() / mTextureView.height.toFloat()
            val cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCurrentCameraId)
            val streamConfigurationMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = streamConfigurationMap!!.getOutputSizes(ImageFormat.JPEG)
            for (i in sizes.indices) {
                val itemSize = sizes[i]
                val itemSizeProportion = itemSize.height.toFloat() / itemSize.width.toFloat()
                val differenceProportion = abs(viewProportion - itemSizeProportion)
                Log.e(TAG, "相减差值比例=$differenceProportion")
                if (i == 0) {
                    selectSize = itemSize
                    selectProportion = differenceProportion
                    continue
                }
                if (differenceProportion <= selectProportion) {
                    if (differenceProportion == selectProportion) {
                        if (selectSize!!.width + selectSize.height < itemSize.width + itemSize.height) {
                            selectSize = itemSize
                            selectProportion = differenceProportion
                        }
                    } else {
                        selectSize = itemSize
                        selectProportion = differenceProportion
                    }
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.e(TAG, "getMatchingSize: 选择的比例是=$selectProportion")
        Log.e(TAG, "getMatchingSize: 选择的尺寸是 宽度=${selectSize?.width}高度=${selectSize?.height}")
        return selectSize!!
    }

    override fun onDestroy() {
        super.onDestroy()
        mCaptureRequest.removeTarget(mSurface)
        mSurface.release()
        mSurfaceTexture.release()
        try {
            mCameraCaptureSession.stopRepeating()
            mCameraCaptureSession.abortCaptures()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        mCameraDevice.close()
        mChildHandler.removeCallbacksAndMessages(null)
        mHandlerThread.quitSafely()
    }

    companion object {
        private val TAG = MainActivity::class.java.name
        private val ORIENTATIONS = SparseIntArray()

        init {
            //为了使照片竖直显示
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    private fun <T> bindLifecycle(lifecycleOwner: LifecycleOwner): LifecycleTransformer<T> {
        return AndroidLifecycle.createLifecycleProvider(lifecycleOwner).bindToLifecycle()
    }
}
