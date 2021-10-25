package com.aram_dev.mapbox.demo

import androidx.appcompat.app.AppCompatActivity
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import android.os.Bundle
import android.widget.Toast
import com.aram_dev.mapbox.R
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*


class SymbolListenerActivity: AppCompatActivity(), OnMapReadyCallback {

    private var mapView: MapView? = null
    private val MAKI_ICON_CAFE = "cafe-15"
    private val MAKI_ICON_HARBOR = "harbor-15"
    private val MAKI_ICON_AIRPORT = "airport-15"
    private var symbolManager: SymbolManager? = null
    private var symbol: Symbol? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        // This contains the MapView in XML and needs to be called after the access token is configured.
        setContentView(R.layout.activity_annotation_plugin_symbol_listener)
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        mapboxMap.setStyle(Style.DARK) { style -> // Set up a SymbolManager instance
            symbolManager = SymbolManager(mapView!!, mapboxMap, style)
            symbolManager!!.iconAllowOverlap = true
            symbolManager!!.textAllowOverlap = true

            // Add symbol at specified lat/lon
            symbol = symbolManager!!.create(
                SymbolOptions()
                    .withLatLng(LatLng(60.169091, 24.939876))
                    .withIconImage(MAKI_ICON_HARBOR)
                    .withIconSize(2.0f)
                    .withDraggable(true)
            )

            // Add click listener and change the symbol to a cafe icon on click
            symbolManager?.addClickListener(OnSymbolClickListener { symbol ->
                Toast.makeText(
                    this@SymbolListenerActivity,
                    getString(R.string.clicked_symbol_toast), Toast.LENGTH_SHORT
                ).show()
                symbol.iconImage = MAKI_ICON_CAFE
                symbolManager?.update(symbol)
                true
            })

            // Add long click listener and change the symbol to an airport icon on long click
            symbolManager?.addLongClickListener(OnSymbolLongClickListener { symbol ->
                Toast.makeText(
                    this@SymbolListenerActivity,
                    getString(R.string.long_clicked_symbol_toast), Toast.LENGTH_SHORT
                ).show()
                symbol.iconImage = MAKI_ICON_AIRPORT
                symbolManager?.update(symbol)
                true
            })
            symbolManager!!.addDragListener(object : OnSymbolDragListener {
                // Left empty on purpose
                override fun onAnnotationDragStarted(annotation: Symbol?) {}

                // Left empty on purpose
                override fun onAnnotationDrag(symbol: Symbol?) {}

                // Left empty on purpose
                override fun onAnnotationDragFinished(annotation: Symbol?) {}
            })
            Toast.makeText(
                this@SymbolListenerActivity,
                getString(R.string.symbol_listener_instruction_toast), Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView!!.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView!!.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView!!.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView!!.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView!!.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView!!.onSaveInstanceState(outState)
    }
}