package com.aram_dev.mapbox.pluginsapp.activity.places

import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aram_dev.mapbox.R

import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.exceptions.MapboxConfigurationException
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import com.mapbox.mapboxsdk.plugins.places.autocomplete.ui.PlaceAutocompleteFragment
import com.mapbox.mapboxsdk.plugins.places.autocomplete.ui.PlaceSelectionListener

class AutocompleteFragmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_places_fragment)

        val autocompleteFragment: PlaceAutocompleteFragment
        if (savedInstanceState == null) {
            val placeOptions = PlaceOptions.builder()
                    .toolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                    .statusbarColor(Color.YELLOW)
                    .hint("Begin searching...")
                    .build()

            autocompleteFragment = PlaceAutocompleteFragment.newInstance(
                    Mapbox.getAccessToken() ?: throw MapboxConfigurationException(),
                    placeOptions
            )

            val transaction = supportFragmentManager.beginTransaction()
            transaction.add(R.id.fragment_container, autocompleteFragment, PlaceAutocompleteFragment.TAG)
            transaction.commit()
        } else {
            autocompleteFragment = supportFragmentManager.findFragmentByTag(PlaceAutocompleteFragment.TAG) as PlaceAutocompleteFragment
        }

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(carmenFeature: CarmenFeature) {
                Toast.makeText(this@AutocompleteFragmentActivity,
                        carmenFeature.text(), Toast.LENGTH_LONG).show()
                finish()
            }

            override fun onCancel() {
                finish()
            }
        })
    }
}
