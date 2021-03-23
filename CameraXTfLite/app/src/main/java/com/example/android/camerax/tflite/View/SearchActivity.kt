package com.example.android.camerax.tflite.View

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.android.example.camerax.tflite.R
import kotlinx.android.synthetic.main.activity_search.*

class SearchActivity : AppCompatActivity(), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        btnSearch.setOnClickListener(this)

    }

    override fun onClick(v: View) {
        if(v.id == R.id.btnSearch){
            val objectName = editObjectName.text.toString().toLowerCase()
            val intent = Intent(this, CameraActivity::class.java)
            val bundle = Bundle()
            bundle.putString("ObjectSearch",objectName)
            intent.putExtras(bundle)
            startActivity(intent)



        }
    }
}