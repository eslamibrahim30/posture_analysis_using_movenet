package com.example.grad_project

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ReportActivityResults : AppCompatActivity() {

    private lateinit var textViewReport: TextView
    private lateinit var imageViewFrontView: ImageView
    private lateinit var imageViewSideView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_results)

        textViewReport = findViewById(R.id.textViewReport)
        imageViewFrontView = findViewById(R.id.imageViewFrontView)
        imageViewSideView = findViewById(R.id.imageViewSideView)

        val patientName = intent.getStringExtra("patientName")
        val patientAge = intent.getStringExtra("patientAge")
        val patientGender = intent.getStringExtra("patientGender")
        val frontViewBitmap = intent.getParcelableExtra<Bitmap>("frontViewBitmap")
        val sideViewBitmap = intent.getParcelableExtra<Bitmap>("sideViewBitmap")

        val reportText = """
            Patient Name: $patientName
            Patient Age: $patientAge
            Patient Gender: $patientGender
        """.trimIndent()

        textViewReport.text = reportText
        imageViewFrontView.setImageBitmap(frontViewBitmap)
        imageViewSideView.setImageBitmap(sideViewBitmap)
    }
}