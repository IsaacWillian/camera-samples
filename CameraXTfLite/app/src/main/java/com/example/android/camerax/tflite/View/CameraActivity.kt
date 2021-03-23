/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camerax.tflite.View

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.android.example.camerax.tflite.R
import com.example.android.camerax.tflite.View.ShowImageActivity
import com.example.android.camerax.tflite.ViewModeFactory.CameraActivityViewModelFactory
import com.example.android.camerax.tflite.ViewModel.CameraActivityViewModel
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.random.Random


/** Activity that displays the camera and performs object detection on the incoming frames */
class CameraActivity : AppCompatActivity() {

    private lateinit var viewModel:CameraActivityViewModel
    private lateinit var viewModelFactory: CameraActivityViewModelFactory
    private lateinit var container: ConstraintLayout
    private lateinit var objectName: String

    private val permissions = listOf(Manifest.permission.CAMERA)
    private val permissionsRequestCode = Random.nextInt(0, 10000)
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        container = findViewById(R.id.camera_container)
        viewModelFactory = CameraActivityViewModelFactory(application)
        viewModel = ViewModelProvider(this, viewModelFactory).get(CameraActivityViewModel::class.java)

        val bundle = intent.extras
        if (bundle != null){
             objectName =  bundle.getString("ObjectSearch").toString()
             viewModel.setObjectSearch(objectName)
        }
        viewModel.cameraActivity = this

        observer()

    }

    //Define observer for Livedata
    private fun observer(){
        // If prediction is not null, show the box and text
        viewModel.predictionSucess.observe(this, Observer{
            if(it != null){

                // Location has to be mapped to our local coordinates
                val location = mapOutputCoordinates(it.location)

                // Update the text and UI
                text_prediction.text = "${"%.2f".format(it.score)} ${it.label}"
                (box_prediction.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    topMargin = location.top.toInt()
                    leftMargin = location.left.toInt()
                    width = min(view_finder.width, location.right.toInt() - location.left.toInt())
                    height = min(view_finder.height, location.bottom.toInt() - location.top.toInt())
                }

                // Make sure all UI elements are visible
                box_prediction.visibility = View.VISIBLE
                text_prediction.visibility = View.VISIBLE
            }else{
                box_prediction.visibility = View.GONE
                text_prediction.visibility = View.GONE
            }

        })

        //Pass frame containing object for another activity
        viewModel.bitmapToShow.observe(this, Observer{

            val bs = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.PNG, 100, bs)
            val intent = Intent(this, ShowImageActivity::class.java)
            intent.putExtra("objectImage", bs.toByteArray())
            startActivity(intent)
            finish()


        })

    }

    /**
     * Helper function used to map the coordinates for objects coming out of
     * the model into the coordinates that the user sees on the screen.
     */
    private fun mapOutputCoordinates(location: RectF): RectF {

        // Step 1: map location to the preview coordinates
        val previewLocation = RectF(
            location.left * view_finder.width,
            location.top * view_finder.height,
            location.right * view_finder.width,
            location.bottom * view_finder.height
        )

        // Step 2: compensate for camera sensor orientation and mirroring
        val isFrontFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
        val correctedLocation = if (isFrontFacing) {
            RectF(
                view_finder.width - previewLocation.right,
                previewLocation.top,
                view_finder.width - previewLocation.left,
                previewLocation.bottom)
        } else {
            previewLocation
        }

        // Step 3: compensate for 1:1 to 4:3 aspect ratio conversion + small margin
        val margin = 0.1f
        val requestedRatio = 4f / 3f
        val midX = (correctedLocation.left + correctedLocation.right) / 2f
        val midY = (correctedLocation.top + correctedLocation.bottom) / 2f
        return if (view_finder.width < view_finder.height) {
            RectF(
                midX - (1f + margin) * requestedRatio * correctedLocation.width() / 2f,
                midY - (1f - margin) * correctedLocation.height() / 2f,
                midX + (1f + margin) * requestedRatio * correctedLocation.width() / 2f,
                midY + (1f - margin) * correctedLocation.height() / 2f
            )
        } else {
            RectF(
                midX - (1f - margin) * correctedLocation.width() / 2f,
                midY - (1f + margin) * requestedRatio * correctedLocation.height() / 2f,
                midX + (1f - margin) * correctedLocation.width() / 2f,
                midY + (1f + margin) * requestedRatio * correctedLocation.height() / 2f
            )
        }
    }

    override fun onResume() {
        super.onResume()

        // Request permissions each time the app resumes, since they can be revoked at any time
        if (!hasPermissions(this)) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), permissionsRequestCode)
        } else {
            viewModel.pauseAnalysis = false
            viewModel.bindCameraUseCases(view_finder)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionsRequestCode && hasPermissions(this)) {

            viewModel.pauseAnalysis = false
            viewModel.bindCameraUseCases(view_finder)
        } else {
            finish() // If we don't have the required permissions, we can't run
        }
    }

    /** Convenience method used to check if all permissions required by this app are granted */
    private fun hasPermissions(context: Context) = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }


}