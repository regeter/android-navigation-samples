/*
 * Copyright 2024 Google LLC
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

package com.example.regeternaviapitest

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.regeternaviapitest.CustomizationPanelsDelegate.logDebugInfo
import com.example.regeternaviapitest.NavFragmentActivity.ButtonConfig
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.navigation.CustomRoutesOptions
import com.google.android.libraries.navigation.DisplayOptions
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationApi.NavigatorListener
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Navigator.RouteStatus
import com.google.android.libraries.navigation.RouteSegment
import com.google.android.libraries.navigation.Waypoint
import com.google.android.libraries.navigation.Waypoint.UnsupportedPlaceIdException
import com.google.android.libraries.places.api.model.Place
import com.google.common.collect.Lists
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope


/**
 * This activity shows a simple Navigation API implementation using a Navigation view and using the
 * Google Places API for destination selection.
 */
private const val TAG = "NavViewActivity"
private const val PLACE_PICKER_REQUEST = 1
private const val LIFECYCLE_TRIGGER_REQUEST = 2

class NavViewActivity : AppCompatActivity() {
  companion object {
    const val INTENT_PLACE_ID = "com.example.regeternaviapitest.PLACE_ID"
    private val routeCancellationExecutor = Executors.newSingleThreadExecutor()
  }
  private lateinit var navView: NavigationView
  var navigatorScope: InitializedNavScope? = null
  var pendingNavActions = mutableListOf<InitializedNavRunnable>()
  private var arrivalListener: Navigator.ArrivalListener? = null
  private var routeChangedListener: Navigator.RouteChangedListener? = null
  private var mPendingRoute: ListenableResultFuture<RouteStatus>? = null
  private lateinit var buttonContainer: LinearLayout
  private var cancellationJob: Job? = null
  private val cancellationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  // Only used to demo the turn-by-turn nav forwarding feature.
  var navInfoDisplayFragment: Fragment? = null

    private val ACTION_DELAY_MS = 50L

  @SuppressLint("MissingPermission") // TODO: requestPermissions(...) in here or earlier
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_nav_view)

    navView = findViewById(R.id.navigation_view)

    navView.onCreate(savedInstanceState)

    val placeIdToNavigate = intent.getStringExtra(INTENT_PLACE_ID)
    if (!placeIdToNavigate.isNullOrEmpty()) {
        try {
            val destination = Waypoint.builder().setPlaceIdString(placeIdToNavigate).build()
            customNavigate(destination)
        } catch (e: UnsupportedPlaceIdException) {
            showToast("Error: Provided Place ID is unsupported.")
            Log.e(TAG, "Unsupported Place ID from intent: $placeIdToNavigate", e)
        }
    }


    // Set up the UI that allows the user to control some NavSDK behaviors in the demo app.
    // These panels set up all the users' selectable options, like whether to show the trip
    // progress bar, whether to force night mode, etc.
    CustomizationPanelsDelegate.initializeCustomizationPanels(this)
    CustomizationPanelsDelegate.setUpNightModeSpinner(this, navView::setForceNightMode)

    // Ensure the screen stays on during nav.
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Register some example listeners for navigation events.
    registerNavigationListeners()

    initializeNavigationApi()

    buttonContainer = findViewById(R.id.button_container)

      // Define button configurations
      val buttonConfigs = listOf(
          ButtonConfig("stop&StartGuidance LifecycleActivity") {
              // `this` inside this lambda refers to NavViewActivity
              // Use lifecycleScope to launch a coroutine tied to the Activity's lifecycle
              lifecycleScope.launch {
                  withNavigatorAsync { navigator.stopGuidance() }
                  delay(500L)
                  startActivity(Intent(this@NavViewActivity, LifecycleTriggerActivity::class.java))
                  delay(10L)
                  withNavigatorAsync { navigator.startGuidance() }
              }
          },
          ButtonConfig("startGuidance") { withNavigatorAsync { navigator.startGuidance() } },
          ButtonConfig("stopGuidance") { withNavigatorAsync { navigator.stopGuidance() } },
          ButtonConfig("clearDestinations") { withNavigatorAsync { navigator.clearDestinations() } },
//          ButtonConfig("Drive: GWC5") {
//              customNavigate(
//                  Waypoint.builder().setLatLng(37.42488508364013, -122.09432661174287).build(),
//              )
//          },
//          ButtonConfig("Drive: GWC5 LifecycleActivity") {
//              customNavigate(
//                  Waypoint.builder().setLatLng(37.42488508364013, -122.09432661174287).build(),
//                  triggerLifecycleActivity = true,
//              )
//          },
          ButtonConfig("Drive") {
              customNavigate(
                  Waypoint.builder().setPlaceIdString("ChIJx1Owgt9_mQAR0CgMWKKWoKU").build(),
              )
          },
          ButtonConfig("Drive 1300ms") {
              lifecycleScope.launch {
                  startActivity(Intent(this@NavViewActivity, LifecycleTriggerActivity::class.java))
                  delay(1300L)
                  customNavigate(
                      Waypoint.builder().setPlaceIdString("ChIJx1Owgt9_mQAR0CgMWKKWoKU").build(),
                  )
              }
          },
          ButtonConfig("Drive 1400ms") {
              lifecycleScope.launch {
                  startActivity(Intent(this@NavViewActivity, LifecycleTriggerActivity::class.java))
                  delay(1400L)
                  customNavigate(
                      Waypoint.builder().setPlaceIdString("ChIJx1Owgt9_mQAR0CgMWKKWoKU").build(),
                  )
              }
          },
          ButtonConfig("Drive 1500ms") {
              lifecycleScope.launch {
                  startActivity(Intent(this@NavViewActivity, LifecycleTriggerActivity::class.java))
                  delay(1500L)
                  customNavigate(
                      Waypoint.builder().setPlaceIdString("ChIJx1Owgt9_mQAR0CgMWKKWoKU").build(),
                  )
              }
          },
//          ButtonConfig("Drive: Rio LifecycleActivity") {
//              customNavigate(
//                  Waypoint.builder().setPlaceIdString("ChIJx1Owgt9_mQAR0CgMWKKWoKU").build(),
//                  triggerLifecycleActivity = true,
//              )
//          },
          ButtonConfig("Drive: Rio (2 Waypoints)") {
              customNavigate(
                  Waypoint.builder().setLatLng(-22.9568329275, -43.196852216).build(),
                  Waypoint.builder().setPlaceIdString("ChIJx1Owgt9_mQAR0CgMWKKWoKU")
                      .build(), // Rua Capitão Salomão, 38, Botafogo, Rio de Janeiro
              )
          },
//          ButtonConfig("Drive: Rio (2 Waypoints) LifecycleActivity") {
//              customNavigate(
//                  Waypoint.builder().setLatLng(-22.9568329275, -43.196852216).build(),
//                  Waypoint.builder().setPlaceIdString("ChIJx1Owgt9_mQAR0CgMWKKWoKU")
//                      .build(), // Rua Capitão Salomão, 38, Botafogo, Rio de Janeiro
//                  triggerLifecycleActivity = true,
//              )
//          },
//          ButtonConfig("Drive: Mock Place Rio") {
//              navigateToPlace(createMockPlace("ChIJx1Owgt9_mQAR0CgMWKKWoKU", -22.944742, -43.180968))
//          },
//          ButtonConfig("Rio (Place Lookup Minimal)") {
//              Handler(Looper.getMainLooper()).postDelayed({
//                  val intent = Intent(this@NavViewActivity, PlacePickerActivity::class.java).apply {
//                      putExtra(PlacePickerActivity.EXTRA_PROGRAMMATIC_LOOKUP_ADDRESS, "Rua Capitão Salomão, 38, Botafogo, Rio de Janeiro, Brazil")
//                  }
//                  startActivityForResult(intent, PLACE_PICKER_REQUEST)
//              }, 100)
//          },
          ButtonConfig("continueToNextDestination") { withNavigatorAsync { navigator.continueToNextDestination() } },

          ButtonConfig("showRouteOverview") { withNavigatorAsync { navView.showRouteOverview() } },

          ButtonConfig("Trigger Lifecycle Only") {
              startActivity(Intent(this@NavViewActivity, LifecycleTriggerActivity::class.java))
          },

          )


//   ChIJx1Owgt9_mQAR0CgMWKKWoKU
      // Rua Capitão Salomão, 38, Botafogo, Rio de Janeiro

    // Add buttons dynamically
    buttonConfigs.forEach { config ->
      addButton(config)
    }


  }

  private fun addButton(config: ButtonConfig) {
    val button = Button(this, null, 0, R.style.SmallButton).apply {
      text = config.text
      setOnClickListener {
//        showToast(config.text)
        config.action()
      }
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.END
        setMargins(0, -8, 0, -8)
      }
    }

    buttonContainer.addView(button)
  }

  /**
   * Runs [block] once map is initialized. Block is ignored if map is never initialized.
   *
   * This ensures that calls using the map before the map is initialized gets executed after the map
   * has been initialized.
   */
  private fun withMapAsync(block: InitializedMapScope.() -> Unit) {
    navView.getMapAsync { map ->
      object : InitializedMapScope {
          override val map = map
        }
        .block()
    }
  }

  /**
   * Runs [block] once navigator is initialized. Block is ignored if the navigator is never
   * initialized (error, etc.).
   *
   * This ensures that calls using the navigator before the navigator is initialized gets executed
   * after the navigator has been initialized.
   */
  private fun withNavigatorAsync(block: InitializedNavRunnable) {
    val navigatorScope = navigatorScope
    if (navigatorScope != null) {
      navigatorScope.block()
    } else {
      pendingNavActions.add(block)
    }
  }

  /** Starts the Navigation API, capturing a reference when ready. */
  private fun initializeNavigationApi() {
    NavigationApi.getNavigator(
      this,
      object : NavigatorListener {
        override fun onNavigatorReady(navigator: Navigator) {
          val scope = InitializedNavScope(navigator)
          navigatorScope = scope
          pendingNavActions.forEach { block -> scope.block() }
          pendingNavActions.clear()

          // Disables the guidance notifications and shuts down the app and background service
          // when the user dismisses/swipes away the app from Android's recent tasks.
          navigator.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE)
        }

        override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
          when (errorCode) {
            NavigationApi.ErrorCode.NOT_AUTHORIZED -> {
              // Note: If this message is displayed, you may need to check that
              // your API_KEY is specified correctly in AndroidManifest.xml
              // and is been enabled to access the Navigation API
              showToast(
                "Error loading Navigation API: Your API key is " +
                  "invalid or not authorized to use Navigation."
              )
            }
            NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> {
              showToast(
                "Error loading Navigation API: User did not " +
                  "accept the Navigation Terms of Use."
              )
            }
            else -> showToast("Error loading Navigation API: $errorCode")
          }
        }
      },
    )

    withMapAsync {
        CustomizationPanelsDelegate.setUpCameraPerspectiveSpinner(
          this@NavViewActivity,
          map::followMyLocation,
        )
        // The logic below simply helps keep the UI in tune with the underlying SDK state.
        CustomizationPanelsDelegate.registerOnCameraFollowLocationCallback(
          this@NavViewActivity,
          map,
        )

        CustomizationPanelsDelegate.registerOnNavigationUiChangedListener(
          this@NavViewActivity,
          navView::addOnNavigationUiChangedListener,
        )
    }
  }

    /**
     * Registers a number of example event listeners that show an on screen message when certain
     * navigation events occur (e.g. the driver's route changes or the destination is reached).
     */
    private fun registerNavigationListeners() {
        withNavigatorAsync {
            arrivalListener =
                Navigator.ArrivalListener { waypoint ->
                    val waypointName =
                        waypoint?.waypoint ?: waypoint.toString() ?: "Unknown Waypoint"
                    val message = "User has arrived at destination: $waypointName"
                    showToast(message)
                    val currRouteSegment: RouteSegment = navigator.getCurrentRouteSegment()

                    if (currRouteSegment != null) {
                        val latLngs = currRouteSegment.latLngs
                        Log.i(TAG, latLngs.toString())
                    }
                }
            navigator.addArrivalListener(arrivalListener)
            Log.d(TAG, "ArrivalListener registered.")



            routeChangedListener =
                Navigator.RouteChangedListener { // Show an onscreen message when the route changes
                    Log.i(TAG, "onRouteChanged: the driver's route changed")
                }
            navigator.addRouteChangedListener(routeChangedListener)
            Log.d(TAG, "RouteChangedListener registered.")
        }
    }

  /**
   * Requests directions from the user's current location to a specific place (provided by the
   * Google Places API).
   */
  private fun navigateToPlace(place: Place) {
    val waypoint: Waypoint? =
      if (place.types?.contains(Place.Type.GEOCODE) == true) {
        // An example of setting a destination via Lat-Lng.
        // Note: Setting LatLng destinations can result in poor routing quality/ETA calculation.
        // Wherever possible you should use a Place ID to describe the destination accurately.
          showToast("destination via Lat-Lng")
        place.latLng?.let { Waypoint.builder().setLatLng(it.latitude, it.longitude).build() }
      } else {
        // Set a destination by using a Place ID (the recommended method)
        try {
            showToast(place.id)
          Waypoint.builder().setPlaceIdString(place.id).build()
        } catch (e: UnsupportedPlaceIdException) {
          showToast("Place ID was unsupported.")
          return
        }
      }

    withNavigatorAsync {
      val pendingRoute = navigator.setDestination(waypoint)

      // Set an action to perform when a route is determined to the destination
      pendingRoute?.setOnResultListener { code ->
        when (code) {
          RouteStatus.OK -> {
            // Hide the toolbar to maximize the navigation UI
            actionBar?.hide()

            // Enable voice audio guidance (through the device speaker)
            navigator.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)

            // Simulate vehicle progress along the route (for demo/debug builds)
//            if (BuildConfig.DEBUG) {
//              navigator.simulator.simulateLocationsAlongExistingRoute(
//                SimulationOptions().speedMultiplier(5f)
//              )
//            }

            // Start turn-by-turn guidance along the current route
            navigator.startGuidance()
          }
          RouteStatus.ROUTE_CANCELED -> showToast("Route guidance cancelled.")
          RouteStatus.NO_ROUTE_FOUND,
          RouteStatus.NETWORK_ERROR ->
            // TODO: Add logic to handle when a route could not be determined
            showToast("Error starting guidance: $code")
          else -> showToast("Error starting guidance: $code")
        }
      }
    }
  }

    private fun customNavigate(
        vararg waypoints: Waypoint,
        routeToken: String? = null,
        triggerLifecycleActivity: Boolean = false
    ) {
        if (waypoints.isEmpty()) {
            showToast("Cannot navigate without at least one destination.")
            Log.e(TAG, "customNavigate called with no waypoints.")
            return
        }

        val setDestinationsAction = {
            Log.d(TAG, "customNavigate: Proceeding to set destinations.")
            val destinations = Lists.newArrayList<Waypoint>()
            destinations.addAll(waypoints)
            withNavigatorAsync {
                val routeStatusFuture = if (routeToken != null) {
                    Log.d(TAG, "customNavigate: Using CustomRoutesOptions.")
                    navigator.setDestinations(
                        destinations,
                        CustomRoutesOptions.builder().setRouteToken(routeToken)
                            .setTravelMode(CustomRoutesOptions.TravelMode.DRIVING).build(),
                        DisplayOptions()
                    )
                } else {
                    Log.d(TAG, "customNavigate: Not using CustomRoutesOptions.")
                    navigator.setDestinations(destinations)
                }
                routeStatusFuture?.setOnResultListener { result ->
                    result?.let { status ->
                        Log.d(TAG, "customNavigate Route Status: $status")
                        if (status == RouteStatus.OK) navigator.startGuidance()
                    } ?: showToast("customNavigate Route Status is null")
                } ?: Log.e(TAG, "customNavigate: routeStatusFuture was null")
            }
        }

        if (triggerLifecycleActivity) {
            Log.d(TAG, "customNavigate: Triggering lifecycle activity.")
            startActivity(Intent(this, LifecycleTriggerActivity::class.java))
            Handler(Looper.getMainLooper()).post {
                Log.d(TAG, "customNavigate: Post-lifecycle trigger, setting destinations.")
                setDestinationsAction()
            }
        } else {
            Log.d(TAG, "customNavigate: Setting destinations directly.")
            setDestinationsAction()
        }
    }

  override fun onSaveInstanceState(savedInstanceState: Bundle) {
    super.onSaveInstanceState(savedInstanceState)

    navView.onSaveInstanceState(savedInstanceState)
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    navView.onTrimMemory(level)
  }

  override fun onStart() {
    super.onStart()
    navView.onStart()
  }

  override fun onResume() {
    super.onResume()
    Log.d(TAG, "onResume function")
    navView.onResume()
  }

  override fun onPause() {
    navView.onPause()
    super.onPause()
  }

  override fun onConfigurationChanged(configuration: Configuration) {
    super.onConfigurationChanged(configuration)
    navView.onConfigurationChanged(configuration)
  }

  override fun onStop() {
    navView.onStop()
    super.onStop()
  }

  override fun onDestroy() {
    navView.onDestroy()
    withNavigatorAsync {
      // Unregister event listeners to avoid memory leaks.
      if (arrivalListener != null) {
        navigator.removeArrivalListener(arrivalListener)
      }
      if (routeChangedListener != null) {
        navigator.removeRouteChangedListener(routeChangedListener)
      }

      navigator.simulator?.unsetUserLocation()
      navigator.cleanup()
    }

    super.onDestroy()
    routeCancellationExecutor.shutdown()
    cancellationScope.cancel()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater = menuInflater
    inflater.inflate(R.menu.menu_default, menu)
    return true
  }

  /** If the Place Picker activity returns a destination, starts navigation to that place. */
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
      super.onActivityResult(requestCode, resultCode, data)
      if (requestCode == PLACE_PICKER_REQUEST) {
          if (resultCode == Activity.RESULT_OK && data != null) {
              try {
                  val place: Place = PlacePickerActivity.getPlace(data)
                  navigateToPlace(place)
              } catch (e: Exception) { Log.e(TAG, "onActivityResult: Error getting Place", e) }
          } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
              val customError = PlacePickerActivity.getProgrammaticError(data)
              Log.w(TAG, "PlacePicker: ${customError ?: data.getParcelableExtra<Status>("STATUS")?.statusMessage ?: "Cancelled"}")
          }
      } else if (requestCode == LIFECYCLE_TRIGGER_REQUEST) {
          Log.d(TAG, "Returned from LifecycleTriggerActivity. Result: $resultCode")
          // The navigation action is handled by the Handler.post in customNavigate
      }
  }

  /**
   * Uses the Google Places API Place Picker to choose a destination to navigate to.
   *
   * This method is referenced by the "Set Destination" item in menu_default.xml
   */
  fun showPlacePickerForDestination(v: MenuItem?): Boolean {
    try {
      startActivityForResult(Intent(this, PlacePickerActivity::class.java), PLACE_PICKER_REQUEST)
    } catch (e: Exception) {
      showToast(
        "Could not display Place Picker. Check your API key has the Google" + "Places API enabled."
      )
      Log.e(TAG, Log.getStackTraceString(e))
    }
    return true
  }

  /**
   * Switches the visibility of the UI of the customization panels and the toggle buttons.
   *
   * This method is referenced by the "Switch Customizations UI On/Off" item in menu_default.xml.
   */
  fun switchCustomizationUIVisibility(unused: MenuItem?) {
    CustomizationPanelsDelegate.switchCustomizationUiVisibility(this)
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  //
  // OnClick listeners for various buttons in the customization panels.
  //
  ////////////////////////////////////////////////////////////////////////////////////////////////
  /** Toggles whether the Navigation UI is enabled. */
  fun toggleNavigationUiEnabled(unused: View?) {
    CustomizationPanelsDelegate.toggleNavigationUiEnabled(this, navView::setNavigationUiEnabled)
  }

  /** Toggles navigation forwarding (e.g. for 2-wheeler projection). */
  fun toggleNavFwding(unused: View?) {
    withNavigatorAsync {
      navInfoDisplayFragment =
        CustomizationPanelsDelegate.toggleNavForwarding(
          this@NavViewActivity,
          navigator,
          navInfoDisplayFragment,
        )
    }
  }

  /** Moves the position of the camera to hover over Melbourne. */
  fun moveCameraToMelbourne(unused: View?) {
    withMapAsync { CustomizationPanelsDelegate.moveCameraToMelbourne(this@NavViewActivity, map) }
  }

  /** Toggles whether the location marker is enabled. */
  fun toggleSetMyLocationEnabled(unused: View?) {
    withMapAsync {
      CustomizationPanelsDelegate.toggleSetMyLocationEnabled(this@NavViewActivity, map)
    }
  }

  /** Toggles the visibility of the Trip Progress Bar UI. This is an EXPERIMENTAL FEATURE. */
  fun toggleTripProgressBarUi(unused: View?) {
    CustomizationPanelsDelegate.toggleTripProgressBarUI(this, navView::setTripProgressBarEnabled)
  }

  /** Logs some debug information to the logcat from the Navigator, upon user request. */
  fun logDebugInfo(unused: View?) {
    withNavigatorAsync {
      navigator.logDebugInfo()
      showToast("Check the logcat for some information about your trip!")
    }
  }

  private fun showToast(errorMessage: String) {
    Toast.makeText(this@NavViewActivity, errorMessage, Toast.LENGTH_LONG).show()
      Log.i("MyTag", errorMessage);
  }

    fun createMockPlace(placeId: String, lat: Double, lng: Double): Place {
        // Create a custom implementation of Place
        return object : Place() {
            override fun getId(): String = placeId
            override fun getLatLng(): LatLng = LatLng(lat, lng)
            override fun getTypes(): List<Place.Type> = listOf(Place.Type.STREET_ADDRESS)

            // The rest of these methods return null as we're not using them
            override fun getAddress(): String? = null
            override fun getAddressComponents(): com.google.android.libraries.places.api.model.AddressComponents? = null
            override fun getName(): String? = null
            override fun getOpeningHours(): com.google.android.libraries.places.api.model.OpeningHours? = null
            override fun getPhoneNumber(): String? = null
            override fun getPhotoMetadatas(): List<com.google.android.libraries.places.api.model.PhotoMetadata>? = null
            override fun getPlusCode(): com.google.android.libraries.places.api.model.PlusCode? = null
            override fun getPriceLevel(): Int? = null
            override fun getRating(): Double? = null
            override fun getUserRatingsTotal(): Int? = null
            override fun getViewport(): com.google.android.gms.maps.model.LatLngBounds? = null
            override fun getWebsiteUri(): android.net.Uri? = null
            override fun getUtcOffsetMinutes(): Int? = null
            override fun getIconUrl(): String? = null
            override fun getIconBackgroundColor(): Int? = null
            override fun describeContents(): Int = 0
            override fun writeToParcel(dest: Parcel, flags: Int) {}
            override fun getBusinessStatus(): BusinessStatus? = null
            override fun getAttributions(): List<String>? = null
        }
    }
}
