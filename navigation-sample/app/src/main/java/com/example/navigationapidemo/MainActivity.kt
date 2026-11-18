/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.navigationapidemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationApi.NavigatorListener
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator

class MainActivity : AppCompatActivity() {

  private lateinit var navContainer: FrameLayout
  private lateinit var navView: NavigationView
  private lateinit var txtStatus: TextView

  private lateinit var btnStartNav: Button
  private lateinit var btnCancelExit: Button
  private lateinit var btnToggleSpeed: Button
  private lateinit var btnRepro100: Button
  private lateinit var btnTestWorkaround: Button
  private lateinit var btnResetNav: Button

  private var navigator: Navigator? = null
  private var isNavViewDestroyed = false

  // Simulated device/network delay speeds
  private val speedPresets = listOf(
    SpeedPreset("Slow Phone", 1200L),
    SpeedPreset("Normal Phone", 500L),
    SpeedPreset("Fast Phone", 100L),
  )
  private var currentSpeedIndex = 0

  private val mainHandler = Handler(Looper.getMainLooper())
  private var pendingNavTransition: Runnable? = null

  private data class SpeedPreset(val name: String, val delayMs: Long)

  private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
      val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
      val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
      if (fineGranted || coarseGranted) {
        initializeNavigationApi()
      } else {
        Toast.makeText(
          this,
          "Location permission is required for Navigation SDK",
          Toast.LENGTH_LONG,
        ).show()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    navContainer = findViewById(R.id.nav_container)
    navView = findViewById(R.id.navigation_view)
    txtStatus = findViewById(R.id.txt_status)

    btnStartNav = findViewById(R.id.btn_start_nav)
    btnCancelExit = findViewById(R.id.btn_cancel_exit)
    btnToggleSpeed = findViewById(R.id.btn_toggle_speed)
    btnRepro100 = findViewById(R.id.btn_repro_100)
    btnTestWorkaround = findViewById(R.id.btn_test_workaround)
    btnResetNav = findViewById(R.id.btn_reset_nav)

    val btnInfoStartNav: Button = findViewById(R.id.btn_info_start_nav)
    val btnInfoCancelExit: Button = findViewById(R.id.btn_info_cancel_exit)
    val btnInfoSpeed: Button = findViewById(R.id.btn_info_speed)
    val btnInfoWorkaround: Button = findViewById(R.id.btn_info_workaround)

    btnInfoStartNav.setOnClickListener {
      showInfoDialog(
        title = "Button 1: Start Nav / Enable UI",
        message = "Initiates navigation and triggers the navigation UI layout transition.\n\n" +
          "Under the hood, real-world apps take time to calculate routes, fetch server tokens, and prepare UI views. We simulate this in-flight delay with the Phone Speed setting (100ms – 1200ms).\n\n" +
          "• Banner turns YELLOW: Navigation setup is in-flight (the race window is OPEN).\n" +
          "• Banner turns GREEN: Navigation setup completed and verified while attached (safe).",
      )
    }

    btnInfoCancelExit.setOnClickListener {
      showInfoDialog(
        title = "Button 2: Cancel / Exit & Repro Guide",
        message = "Detaches and destroys the NavigationView (simulating a user cancelling a trip or pressing Back).\n\n" +
          "HOW TO REPRODUCE CRASH:\n" +
          "1. Set Phone Speed to 'Slow Phone (1200ms)'.\n" +
          "2. Tap '1. Start Nav / Enable UI'.\n" +
          "3. IMMEDIATELY tap '2. Cancel / Exit (Detach)' while the banner is still YELLOW.\n" +
          "-> The app crashes immediately on the next looper turn with ApiIllegalStateException!\n\n" +
          "HOW TO AVOID CRASH:\n" +
          "Tap '1. Start Nav', WAIT until the banner turns GREEN (ready), then tap '2. Cancel / Exit'. No crash occurs!",
      )
    }

    btnInfoSpeed.setOnClickListener {
      showInfoDialog(
        title = "How Phone Speed Simulation Works",
        message = "In real navigation apps, startup latency varies based on device hardware and load:\n\n" +
          "• Slow Phone (1200ms): Simulates low-end devices, background CPU throttling, or slow network routing. Leaves a 1.2s window so you can easily tap Cancel in quick succession and trigger the race condition.\n\n" +
          "• Normal Phone (500ms): Standard latency requiring a fast double-tap.\n\n" +
          "• Fast Phone (100ms): High-end device where setup finishes in 100ms. It is almost impossible to tap Cancel before it turns green, proving why faster devices rarely experience the bug.",
      )
    }

    btnInfoWorkaround.setOnClickListener {
      showInfoDialog(
        title = "Instant Repro vs Workaround",
        message = "• 100% Repro: Forces the exact race condition deterministically in a single Main Looper turn. Always crashes immediately.\n\n" +
          "• Workaround (Safe Detach): Calling navView.setNavigationUiEnabled(false) immediately before teardown cancels pending layout verifications, completely preventing the crash even during rapid cancellations.",
      )
    }

    // Layout delegate must be set BEFORE navView.onCreate:
    navView.setLayoutDelegate(MinimalLayoutDelegate())
    navView.onCreate(savedInstanceState)

    setNavButtonsEnabled(false)
    updateSpeedButtonText()

    // --- 1. Interactive Race Condition: Button A ("Start Nav / Enable UI") ---
    btnStartNav.setOnClickListener {
      if (isNavViewDestroyed) {
        Toast.makeText(this, "NavView is destroyed. Tap Reset first.", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      // Cancel any existing pending transition
      pendingNavTransition?.let { mainHandler.removeCallbacks(it) }

      val preset = speedPresets[currentSpeedIndex]
      txtStatus.text = "🟡 Starting Nav (${preset.name}: ${preset.delayMs}ms)... TAP CANCEL NOW!"
      txtStatus.setTextColor(Color.parseColor("#FFD700"))

      navView.setNavigationUiEnabled(false)

      val transition = Runnable {
        pendingNavTransition = null
        if (!isNavViewDestroyed) {
          // Normal case: user did NOT exit quickly -> layout verified while attached
          navView.setNavigationUiEnabled(true)
          txtStatus.text = "🟢 Nav Ready & Verified! (Safe to tap Cancel now - won't crash)"
          txtStatus.setTextColor(Color.parseColor("#00E676"))
        }
      }
      pendingNavTransition = transition
      mainHandler.postDelayed(transition, preset.delayMs)
    }

    // --- 2. Interactive Race Condition: Button B ("Cancel / Exit (Detach)") ---
    btnCancelExit.setOnClickListener {
      if (isNavViewDestroyed) {
        Toast.makeText(this, "NavView already destroyed. Tap Reset first.", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      val transition = pendingNavTransition
      if (transition != null) {
        // User pressed in quick succession during the in-flight window!
        mainHandler.removeCallbacks(transition)
        pendingNavTransition = null

        txtStatus.text = "💥 CAUGHT RACE WINDOW! Fast exit while nav starting -> CRASHING!"
        txtStatus.setTextColor(Color.RED)

        // The exact race: nav UI enabled right as teardown occurs in the same turn:
        navView.setNavigationUiEnabled(true)
        teardownNavView(safe = false)
      } else {
        // User waited for verification to finish before pressing exit -> safe!
        teardownNavView(safe = false)
        txtStatus.text = "🔴 Detached AFTER verification -> NO CRASH!"
        txtStatus.setTextColor(Color.parseColor("#B0BEC5"))
        Toast.makeText(
          this,
          "No crash: You waited until verification completed! Tap Reset, then Start Nav and quickly Cancel to crash.",
          Toast.LENGTH_LONG,
        ).show()
      }
    }

    // --- 3. Speed Simulator Toggle ---
    btnToggleSpeed.setOnClickListener {
      currentSpeedIndex = (currentSpeedIndex + 1) % speedPresets.size
      updateSpeedButtonText()
      val preset = speedPresets[currentSpeedIndex]
      Toast.makeText(
        this,
        "Simulated phone speed: ${preset.name} (${preset.delayMs}ms window)",
        Toast.LENGTH_SHORT,
      ).show()
    }

    // --- 4. 100% Repro (Instant Detach in same turn) ---
    btnRepro100.setOnClickListener {
      pendingNavTransition?.let { mainHandler.removeCallbacks(it) }
      pendingNavTransition = null
      navView.setNavigationUiEnabled(false)
      navView.setNavigationUiEnabled(true)
      teardownNavView(safe = false)
    }

    // --- 5. Workaround (Safe Detach) ---
    btnTestWorkaround.setOnClickListener {
      pendingNavTransition?.let {
        mainHandler.removeCallbacks(it)
        pendingNavTransition = null
      }
      teardownNavView(safe = true)
      txtStatus.text = "🟢 Workaround applied: Safely disabled UI before teardown!"
      txtStatus.setTextColor(Color.parseColor("#00E676"))
      Toast.makeText(this, "Workaround applied: No crash!", Toast.LENGTH_SHORT).show()
    }

    // --- 6. Reset NavView ---
    btnResetNav.setOnClickListener {
      recreateNavView()
    }

    checkAndRequestPermissions()
  }

  private fun updateSpeedButtonText() {
    val preset = speedPresets[currentSpeedIndex]
    btnToggleSpeed.text = "Simulate Speed: ${preset.name} (${preset.delayMs}ms)"
  }

  private fun teardownNavView(safe: Boolean) {
    if (isNavViewDestroyed) return
    isNavViewDestroyed = true

    if (safe) {
      navView.setNavigationUiEnabled(false)
    }

    (navView.parent as? ViewGroup)?.removeView(navView)
    navView.onPause()
    navView.onStop()
    navView.onDestroy()

    updateNavButtonsState()
  }

  private fun recreateNavView() {
    pendingNavTransition?.let {
      mainHandler.removeCallbacks(it)
      pendingNavTransition = null
    }

    if (!isNavViewDestroyed) {
      teardownNavView(safe = true)
    }

    navContainer.removeAllViews()

    navView = NavigationView(this).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }
    navContainer.addView(navView)

    navView.setLayoutDelegate(MinimalLayoutDelegate())
    navView.onCreate(null)
    navView.onStart()
    navView.onResume()

    isNavViewDestroyed = false
    txtStatus.text = getString(R.string.status_ready_text)
    txtStatus.setTextColor(Color.WHITE)

    updateNavButtonsState()
    Toast.makeText(this, "NavigationView reset and ready", Toast.LENGTH_SHORT).show()
  }

  private fun updateNavButtonsState() {
    val isReady = !isNavViewDestroyed && navigator != null
    btnStartNav.isEnabled = isReady
    btnCancelExit.isEnabled = !isNavViewDestroyed
    btnRepro100.isEnabled = isReady
    btnTestWorkaround.isEnabled = !isNavViewDestroyed
  }

  private fun setNavButtonsEnabled(enabled: Boolean) {
    btnStartNav.isEnabled = enabled
    btnCancelExit.isEnabled = enabled
    btnRepro100.isEnabled = enabled
    btnTestWorkaround.isEnabled = enabled
  }

  private fun checkAndRequestPermissions() {
    val fineGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    if (fineGranted || coarseGranted) {
      initializeNavigationApi()
    } else {
      requestPermissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
      )
    }
  }

  private fun initializeNavigationApi() {
    NavigationApi.getNavigator(
      this,
      object : NavigatorListener {
        override fun onNavigatorReady(navigator: Navigator) {
          this@MainActivity.navigator = navigator
          if (!isNavViewDestroyed) {
            updateNavButtonsState()
          }
          txtStatus.text = "🟢 Navigator Ready (Idle)"
          txtStatus.setTextColor(Color.WHITE)
          Toast.makeText(this@MainActivity, "Navigator ready", Toast.LENGTH_SHORT).show()
        }

        override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
          when (errorCode) {
            NavigationApi.ErrorCode.NOT_AUTHORIZED -> {
              Toast.makeText(
                this@MainActivity,
                "Navigation API error: API key not authorized",
                Toast.LENGTH_LONG,
              ).show()
            }
            NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> {
              Toast.makeText(
                this@MainActivity,
                "Navigation API error: Terms not accepted",
                Toast.LENGTH_LONG,
              ).show()
            }
            else -> {
              Toast.makeText(
                this@MainActivity,
                "Navigation API error: $errorCode",
                Toast.LENGTH_LONG,
              ).show()
            }
          }
        }
      },
    )
  }

  override fun onStart() {
    super.onStart()
    if (!isNavViewDestroyed) {
      navView.onStart()
    }
  }

  override fun onResume() {
    super.onResume()
    if (!isNavViewDestroyed) {
      navView.onResume()
    }
  }

  override fun onPause() {
    if (!isNavViewDestroyed) {
      navView.onPause()
    }
    super.onPause()
  }

  override fun onStop() {
    if (!isNavViewDestroyed) {
      navView.onStop()
    }
    super.onStop()
  }

  override fun onDestroy() {
    pendingNavTransition?.let {
      mainHandler.removeCallbacks(it)
      pendingNavTransition = null
    }
    if (!isNavViewDestroyed) {
      navView.onDestroy()
    }
    super.onDestroy()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    if (!isNavViewDestroyed) {
      navView.onSaveInstanceState(outState)
    }
  }

  private fun showInfoDialog(title: String, message: String) {
    androidx.appcompat.app.AlertDialog.Builder(this)
      .setTitle(title)
      .setMessage(message)
      .setPositiveButton("Got it", null)
      .show()
  }
}
