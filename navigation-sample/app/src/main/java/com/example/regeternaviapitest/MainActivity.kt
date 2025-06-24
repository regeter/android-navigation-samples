package com.example.regeternaviapitest

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder // Import URLEncoder

class MainActivity : AppCompatActivity() {
  public override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    // --- Initialize Buttons ---
    findViewById<Button>(R.id.button_navigate_maps).setOnClickListener {
      val addressQuery = "Rua Capitão Salomão, 38 - Botafogo, Rio de Janeiro - RJ, 22271-040, Brasil"
      try {
        // URL-encode the query
        val encodedQuery = URLEncoder.encode(addressQuery, "UTF-8")
        val gmmIntentUri = Uri.parse("google.navigation:q=$encodedQuery&mode=d") // 'd' for driving mode
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps") // Target Google Maps app

        // Verify that Google Maps can handle the intent
        if (mapIntent.resolveActivity(packageManager) != null) {
          startActivity(mapIntent)
        } else {
          Toast.makeText(this, "Google Maps app not found or cannot handle this request.", Toast.LENGTH_LONG).show()
        }
      } catch (e: Exception) { // Catch broader exceptions for encoding or activity not found
        Toast.makeText(this, "Error preparing navigation intent.", Toast.LENGTH_SHORT).show()
        e.printStackTrace() // For debugging
      }
    }

    // Existing ListView setup
    val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, DEMOS.keys.toList())
    val listView = findViewById<ListView>(R.id.list_view)
    listView.adapter = listAdapter
    listView.onItemClickListener = OnItemClickListener { parent, view, position, _ ->
      val demoName = parent.getItemAtPosition(position) as String
      startActivity(Intent(view.context, DEMOS[demoName]))
    }
  }

  companion object {
    private val DEMOS =
      mapOf<String, Class<*>>(
        "NavViewActivity" to NavViewActivity::class.java,
        "NavFragmentActivity" to NavFragmentActivity::class.java,
        "SwappingMapAndNavActivity" to SwappingMapAndNavActivity::class.java,
      )
  }
}