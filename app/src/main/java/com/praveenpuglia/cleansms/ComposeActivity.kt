package com.praveenpuglia.cleansms

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.content.Intent

class ComposeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose)

        val addressField = findViewById<EditText>(R.id.compose_address)
        val bodyField = findViewById<EditText>(R.id.compose_body)
        val sendBtn = findViewById<Button>(R.id.compose_send_btn)

        // Pre-fill from SENDTO intent (e.g. sms:+1234567890)
        intent?.data?.let { uri: Uri ->
            if (uri.scheme?.startsWith("sms") == true || uri.scheme?.startsWith("mms") == true) {
                val number = uri.schemeSpecificPart.substringBefore('?')
                addressField.setText(number)
            }
        }

        sendBtn.setOnClickListener {
            // Placeholder send logic (actual sending requires being default SMS app)
            finish()
        }
    }
}
