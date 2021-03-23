package com.example.android.camerax.tflite.ViewModeFactory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.android.camerax.tflite.ViewModel.CameraActivityViewModel

//ViewModelFactory to pass argument to ViewModel

class CameraActivityViewModelFactory(private val application: Application):ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>):T {
        if(modelClass.isAssignableFrom(CameraActivityViewModel::class.java)){
            return CameraActivityViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}