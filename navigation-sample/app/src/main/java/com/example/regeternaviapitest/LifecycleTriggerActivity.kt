package com.example.regeternaviapitest

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * A minimal, invisible activity that finishes immediately.
 * Its sole purpose is to trigger the lifecycle methods (onPause/onResume)
 * of the activity that launched it.
 */
class LifecycleTriggerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MyTag", "Hello and Goodbye from LifecycleTriggerActivity")
        // No setContentView needed as it's finishing immediately.
        finish() // Immediately finish and return control.
    }
}