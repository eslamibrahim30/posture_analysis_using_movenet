package com.example.grad_project

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val reportActivityBtn: ImageButton = findViewById(R.id.imageButtonReport )
        val correctorActivityBtn: ImageButton = findViewById(R.id.imageButtonCorrector )

        reportActivityBtn.setOnClickListener {
            val reportIntent = Intent(this, ReportActivityPatientInfo::class.java)
            startActivity(reportIntent)
        }
        correctorActivityBtn.setOnClickListener {
            val correctorIntent = Intent(this, CorrectorActivity::class.java)
            startActivity(correctorIntent)
        }
    }
}