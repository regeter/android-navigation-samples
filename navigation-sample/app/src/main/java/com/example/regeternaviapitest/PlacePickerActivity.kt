package com.example.regeternaviapitest

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import java.util.Arrays

/** An activity to host AutocompleteSupportFragment from Places SDK. */
class PlacePickerActivity : AppCompatActivity() {
  private var placesClientInstance: PlacesClient? = null
  private val localTag = "PlacePickerActivity"

  companion object {
    const val EXTRA_PROGRAMMATIC_LOOKUP_ADDRESS = "PROGRAMMATIC_LOOKUP_ADDRESS"
    const val EXTRA_PROGRAMMATIC_ERROR = "PROGRAMMATIC_ERROR"

    fun getPlace(data: Intent): Place {
      return data.getParcelableExtra("PLACE")!!
    }
    fun getProgrammaticError(data: Intent): String? {
      return data.getStringExtra(EXTRA_PROGRAMMATIC_ERROR)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val programmaticAddress = intent.getStringExtra(EXTRA_PROGRAMMATIC_LOOKUP_ADDRESS)
    if (programmaticAddress != null) {
      Log.d(localTag, "Programmatic mode for address: $programmaticAddress")
      if (!Places.isInitialized()) {
        Log.e(localTag, "Places SDK not initialized for programmatic lookup.")
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_PROGRAMMATIC_ERROR, "Places SDK not initialized."))
        finish()
        return
      }
      placesClientInstance = Places.createClient(this)
      initiateProgrammaticAddressLookup(programmaticAddress)
      return // Skip UI setup
    }

    // Original UI setup code
    setContentView(R.layout.activity_place_picker)
    val autocompleteFragment =
      supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
              as AutocompleteSupportFragment?

    autocompleteFragment?.setPlaceFields(
      Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.TYPES, Place.Field.ADDRESS)
    )

    autocompleteFragment?.setOnPlaceSelectedListener(
      object : PlaceSelectionListener {
        override fun onPlaceSelected(place: Place) {
          setResult(Activity.RESULT_OK, Intent().putExtra("PLACE", place))
          finish()
        }

        override fun onError(status: Status) {
          setResult(Activity.RESULT_CANCELED, Intent().putExtra("STATUS", status))
          finish()
        }
      }
    )
  }

  private fun initiateProgrammaticAddressLookup(addressQuery: String) {
    val client = placesClientInstance ?: run {
      Log.e(localTag, "PlacesClient not available for lookup.")
      setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_PROGRAMMATIC_ERROR, "Internal error: PlacesClient not ready."))
      finish()
      return
    }

    val token = AutocompleteSessionToken.newInstance()
    val placeFields = Arrays.asList(
      Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG,
      Place.Field.TYPES, Place.Field.ADDRESS
    )

    val predictionsRequest = FindAutocompletePredictionsRequest.builder()
      .setSessionToken(token)
      .setQuery(addressQuery)
      .build()

    client.findAutocompletePredictions(predictionsRequest)
      .addOnSuccessListener { response ->
        if (response.autocompletePredictions.isEmpty()) {
          Log.w(localTag, "No predictions found for: $addressQuery")
          setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_PROGRAMMATIC_ERROR, "No place found for address."))
          finish()
          return@addOnSuccessListener
        }
        val firstPrediction = response.autocompletePredictions[0]
        val placeId = firstPrediction.placeId
        Log.d(localTag, "Found prediction ID: $placeId for $addressQuery")

        val fetchRequest = FetchPlaceRequest.builder(placeId, placeFields)
          .setSessionToken(token)
          .build()

        client.fetchPlace(fetchRequest)
          .addOnSuccessListener { fetchResponse ->
            Log.d(localTag, "Successfully fetched place: ${fetchResponse.place.name}")
            setResult(Activity.RESULT_OK, Intent().putExtra("PLACE", fetchResponse.place))
            finish()
          }
          .addOnFailureListener { exception ->
            handleProgrammaticFailure("Failed to fetch place details for $placeId", exception)
          }
      }
      .addOnFailureListener { exception ->
        handleProgrammaticFailure("Failed to find autocomplete predictions for $addressQuery", exception)
      }
  }

  private fun handleProgrammaticFailure(contextMsg: String, ex: Exception) {
    val errorDetails: String = if (ex is ApiException) {
      "API Error ${ex.statusCode}: ${ex.status.statusMessage ?: "Unknown API issue"}"
    } else {
      ex.localizedMessage ?: "Unknown error during programmatic lookup"
    }
    Log.e(localTag, "$contextMsg - $errorDetails", ex)
    setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_PROGRAMMATIC_ERROR, "$contextMsg - $errorDetails"))
    finish()
  }
}