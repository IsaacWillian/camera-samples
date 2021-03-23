package com.example.android.camerax.tflite.ViewModel

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.View
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.example.android.camera.utils.YuvToRgbConverter
import com.example.android.camerax.tflite.ObjectDetectionHelper
import com.example.android.camerax.tflite.View.CameraActivity
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.util.concurrent.Executors

class CameraActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val mContext =  application.applicationContext
    private val executor = Executors.newSingleThreadExecutor()

    private var mPredictionSucess = MutableLiveData<ObjectDetectionHelper.ObjectPrediction?>()
    val predictionSucess:LiveData<ObjectDetectionHelper.ObjectPrediction?> = mPredictionSucess

    private var mBitmapToShow = MutableLiveData<Bitmap>()
    val bitmapToShow:LiveData<Bitmap> = mBitmapToShow

    private lateinit var bitmapBuffer: Bitmap
    private var objectSearch = ""
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private val isFrontFacing get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    lateinit var cameraActivity: CameraActivity
    var pauseAnalysis = false
    private var imageRotationDegrees: Int = 0
    private val tfImageBuffer = TensorImage(DataType.UINT8)


    fun setObjectSearch(objectName:String){
        objectSearch = objectName
    }

    private val tfImageProcessor by lazy {
        val cropSize = minOf(bitmapBuffer.width, bitmapBuffer.height)
        ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(cropSize, cropSize))
                .add(ResizeOp(
                        tfInputSize.height, tfInputSize.width, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                .add(Rot90Op(-imageRotationDegrees / 90))
                .add(NormalizeOp(0f, 1f))
                .build()
    }

    private val tflite by lazy {
        Interpreter(
                FileUtil.loadMappedFile(mContext, CameraActivityViewModel.MODEL_PATH),
                Interpreter.Options().addDelegate(NnApiDelegate()))
    }

    private val detector by lazy {
        ObjectDetectionHelper(
                tflite,
                FileUtil.loadLabels(mContext, CameraActivityViewModel.LABELS_PATH)
        )
    }

    private val tfInputSize by lazy {
        val inputIndex = 0
        val inputShape = tflite.getInputTensor(inputIndex).shape()
        Size(inputShape[2], inputShape[1]) // Order of axis is: {1, height, width, 3}
    }


    private fun stopAnalysisAndShowFrame() {
        if (!pauseAnalysis) {
            // Otherwise, pause image analysis
            pauseAnalysis = true
            val matrix = Matrix().apply {
                postRotate(imageRotationDegrees.toFloat())
                if (isFrontFacing) postScale(-1f, 1f)
            }
            mBitmapToShow.value = Bitmap.createBitmap(
                    bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)

        }

    }

    @SuppressLint("UnsafeExperimentalUsageError")
    fun bindCameraUseCases(view:PreviewView) {
        view.post {

            val cameraProviderFuture = ProcessCameraProvider.getInstance(mContext)
            cameraProviderFuture.addListener({

                // Camera provider is now guaranteed to be available
                val cameraProvider = cameraProviderFuture.get()

                // Set up the view finder use case to display camera preview
                val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(view.display.rotation)
                        .build()

                // Set up the image analysis use case which will process frames in real time
                val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(view.display.rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                var frameCounter = 0
                var lastFpsTimestamp = System.currentTimeMillis()
                val converter = YuvToRgbConverter(mContext)



                imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer { image ->
                    if (!::bitmapBuffer.isInitialized) {
                        // The image rotation and RGB image buffer are initialized only once
                        // the analyzer has started running
                        imageRotationDegrees = image.imageInfo.rotationDegrees
                        bitmapBuffer = Bitmap.createBitmap(
                                image.width, image.height, Bitmap.Config.ARGB_8888)
                    }

                    // Early exit: image analysis is in paused state
                    if (pauseAnalysis) {
                        image.close()
                        imageAnalysis.clearAnalyzer()
                        return@Analyzer
                    }

                    // Convert the image to RGB and place it in our shared buffer
                    image.use { converter.yuvToRgb(image.image!!, bitmapBuffer) }

                    // Process the image in Tensorflow
                    val tfImage = tfImageProcessor.process(tfImageBuffer.apply { load(bitmapBuffer) })

                    // Perform the object detection for the current frame
                    val predictions = detector.predict(tfImage)

                    // Report only the top prediction
                    reportPrediction(view,predictions.maxByOrNull { it.score })


                    // Compute the FPS of the entire pipeline
                    val frameCount = 10
                    if (++frameCounter % frameCount == 0) {
                        frameCounter = 0
                        val now = System.currentTimeMillis()
                        val delta = now - lastFpsTimestamp
                        val fps = 1000 * frameCount.toFloat() / delta
                        Log.d(TAG, "FPS: ${"%.02f".format(fps)}")
                        lastFpsTimestamp = now
                    }
                })

                // Create a new camera selector each time, enforcing lens facing
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                // Apply declared configs to CameraX using the same lifecycle owner
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                     cameraActivity as LifecycleOwner, cameraSelector, preview, imageAnalysis)

                // Use the camera object to link our preview use case with the view
                preview.setSurfaceProvider(view.surfaceProvider)

            }, ContextCompat.getMainExecutor(mContext))
        }
    }

    private fun reportPrediction(view:View,prediction: ObjectDetectionHelper.ObjectPrediction?) = view.post {

        // Early exit: if prediction is not good enough, don't report it
        if (prediction == null || prediction.score < ACCURACY_THRESHOLD || prediction.label != objectSearch) {
            mPredictionSucess.value = null

            return@post
        }

            mPredictionSucess.value = prediction
            stopAnalysisAndShowFrame()

    }


    companion object {
        private val TAG = CameraActivityViewModel::class.java.simpleName

        private const val ACCURACY_THRESHOLD = 0.5f
        private const val MODEL_PATH = "coco_ssd_mobilenet_v1_1.0_quant.tflite"
        private const val LABELS_PATH = "coco_ssd_mobilenet_v1_1.0_labels.txt"
    }
}