package com.aram_dev.mapbox.demo

import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aram_dev.mapbox.R
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource


class AnimatedMarkerActivity : AppCompatActivity(), OnMapReadyCallback,
    MapboxMap.OnMapClickListener {


    private var mapView: MapView? = null
    private var mapboxMap: MapboxMap? = null
    private var currentPosition = LatLng(64.900932, -18.167040)
    private var geoJsonSource: GeoJsonSource? = null
    private var animator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

// Mapbox access token is configured here. This needs to be called either in your application
// object or in the same activity which contains the mapview.
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

// This contains the MapView in XML and needs to be called after the access token is configured.
        setContentView(R.layout.activity_lab_animated_marker)
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        geoJsonSource = GeoJsonSource(
            "source-id",
            Feature.fromGeometry(
                Point.fromLngLat(currentPosition.longitude, currentPosition.latitude)
            )
        )
        mapboxMap.setStyle(Style.SATELLITE_STREETS) { style ->
            style.addImage(
                "marker_icon", BitmapFactory.decodeResource(
                    resources, R.drawable.map_default_map_marker
                )
            )
            geoJsonSource?.let { style.addSource(it) }
            style.addLayer(
                SymbolLayer("layer-id", "source-id")
                    .withProperties(
                        PropertyFactory.iconImage("marker_icon"),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconAllowOverlap(true)
                    )
            )
            Toast.makeText(
                this@AnimatedMarkerActivity,
                getString(R.string.tap_on_map_instruction),
                Toast.LENGTH_LONG
            ).show()
            mapboxMap.addOnMapClickListener(this@AnimatedMarkerActivity)
        }
    }

    override fun onMapClick(point: LatLng): Boolean {
// When the user clicks on the map, we want to animate the marker to that
// location.
        if (animator != null && animator!!.isStarted) {
            currentPosition = animator!!.animatedValue as LatLng
            animator!!.cancel()
        }
        animator = ObjectAnimator
            .ofObject(latLngEvaluator, currentPosition, point)
            .setDuration(2000)
        animator?.addUpdateListener(animatorUpdateListener)
        animator?.start()
        currentPosition = point
        return true
    }

    private val animatorUpdateListener =
        AnimatorUpdateListener { valueAnimator ->
            val animatedPosition = valueAnimator.animatedValue as LatLng
            geoJsonSource?.setGeoJson(
                Point.fromLngLat(
                    animatedPosition.longitude,
                    animatedPosition.latitude
                )
            )
        }

    // Class is used to interpolate the marker animation.
    private val latLngEvaluator: TypeEvaluator<LatLng> = object : TypeEvaluator<LatLng> {
        private val latLng = LatLng()
        override fun evaluate(fraction: Float, startValue: LatLng, endValue: LatLng): LatLng {
            latLng.latitude = (startValue.latitude
                    + (endValue.latitude - startValue.latitude) * fraction)
            latLng.longitude = (startValue.longitude
                    + (endValue.longitude - startValue.longitude) * fraction)
            return latLng
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (animator != null) {
            animator!!.cancel()
        }
        if (mapboxMap != null) {
            mapboxMap!!.removeOnMapClickListener(this)
        }
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
}