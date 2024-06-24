package com.example.grad_project

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class ReportActivityPatientInfo : AppCompatActivity() {

    private lateinit var editTextPatientName: EditText
    private lateinit var editTextPatientAge: EditText
    private lateinit var radioGroupGender: RadioGroup
    private lateinit var radioMale: RadioButton
    private lateinit var radioFemale: RadioButton
    private lateinit var buttonCaptureFrontView: Button
    private lateinit var buttonSelectFrontView: Button
    private lateinit var buttonCaptureSideView: Button
    private lateinit var buttonSelectSideView: Button
    private lateinit var buttonGenerateReport: Button

    private lateinit var frontViewBitmap: Bitmap
    private lateinit var sideViewBitmap: Bitmap

    companion object {
        const val REQUEST_IMAGE_CAPTURE_FRONT = 1
        const val REQUEST_IMAGE_SELECT_FRONT = 2
        const val REQUEST_IMAGE_CAPTURE_SIDE = 3
        const val REQUEST_IMAGE_SELECT_SIDE = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_patient_info)

        editTextPatientName = findViewById(R.id.editTextPatientName)
        editTextPatientAge = findViewById(R.id.editTextPatientAge)
        radioGroupGender = findViewById(R.id.radioGroupGender)
        radioMale = findViewById(R.id.radioMale)
        radioFemale = findViewById(R.id.radioFemale)
        buttonCaptureFrontView = findViewById(R.id.buttonCaptureFrontView)
        buttonSelectFrontView = findViewById(R.id.buttonSelectFrontView)
        buttonCaptureSideView = findViewById(R.id.buttonCaptureSideView)
        buttonSelectSideView = findViewById(R.id.buttonSelectSideView)
        buttonGenerateReport = findViewById(R.id.buttonGenerateReport)

        buttonCaptureFrontView.setOnClickListener {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE_FRONT)
        }

        buttonSelectFrontView.setOnClickListener {
            val selectPictureIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(selectPictureIntent, REQUEST_IMAGE_SELECT_FRONT)
        }

        buttonCaptureSideView.setOnClickListener {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE_SIDE)
        }

        buttonSelectSideView.setOnClickListener {
            val selectPictureIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(selectPictureIntent, REQUEST_IMAGE_SELECT_SIDE)
        }

        buttonGenerateReport.setOnClickListener {
            val patientName = editTextPatientName.text.toString()
            val patientAge = editTextPatientAge.text.toString()
            val patientGender = if (radioMale.isChecked) "Male" else "Female"

            val intent = Intent(this, ReportActivityResults::class.java)
            intent.putExtra("patientName", patientName)
            intent.putExtra("patientAge", patientAge)
            intent.putExtra("patientGender", patientGender)
            intent.putExtra("frontViewBitmap", frontViewBitmap)
            intent.putExtra("sideViewBitmap", sideViewBitmap)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE_FRONT -> {
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    frontViewBitmap = imageBitmap
                }
                REQUEST_IMAGE_SELECT_FRONT -> {
                    val imageUri = data?.data
                    frontViewBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                }
                REQUEST_IMAGE_CAPTURE_SIDE -> {
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    sideViewBitmap = imageBitmap
                }
                REQUEST_IMAGE_SELECT_SIDE -> {
                    val imageUri = data?.data
                    sideViewBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                }
            }
        }
    }
}