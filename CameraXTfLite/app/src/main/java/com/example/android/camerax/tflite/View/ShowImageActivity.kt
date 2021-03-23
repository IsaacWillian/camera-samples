package com.example.android.camerax.tflite.View

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.android.example.camerax.tflite.R
import kotlinx.android.synthetic.main.activity_show_image.*

class ShowImageActivity : AppCompatActivity(), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_image)

        val b = intent.getByteArrayExtra(("objectImage"))
        val bitmap = b?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
        image.setImageBitmap(bitmap)

        newSearch.setOnClickListener(this)

        }

    override fun onClick(v: View) {
        if(v.id == R.id.newSearch){
            finish()

        }

    }
}