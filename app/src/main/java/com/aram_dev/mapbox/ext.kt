package com.aram_dev.mapbox

import android.content.Context
import android.graphics.BitmapFactory
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource

fun MapboxMap.addFeatures(
    requireContext: Context,
    symbolLayerIconFeatureList: MutableList<Feature>
) {

    val SOURCE_ID = "SOURCE_ID"
    val ICON_ID = "ICON_ID"
    val LAYER_ID = "LAYER_ID"

    setStyle(
        Style.Builder()
            .fromUri(Style.SATELLITE_STREETS)
            // Add the SymbolLayer icon image to the map style
            .withImage(
                ICON_ID, BitmapFactory.decodeResource(
                    requireContext.resources, R.drawable.mapbox_marker_icon_default
                )
            )

            // Adding a GeoJson source for the SymbolLayer icons.
            .withSource(
                GeoJsonSource(
                    SOURCE_ID,
                    FeatureCollection.fromFeatures(symbolLayerIconFeatureList)
                )
            )

            // Adding the actual SymbolLayer to the map style. An offset is added that the bottom of the red
            // marker icon gets fixed to the coordinate, rather than the middle of the icon being fixed to
            // the coordinate point. This is offset is not always needed and is dependent on the image
            // that you use for the SymbolLayer icon.
            .withLayer(
                SymbolLayer(LAYER_ID, SOURCE_ID)
                    .withProperties(
                        PropertyFactory.iconImage(ICON_ID),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
            ), Style.OnStyleLoaded() {
            // Map is set up and the style has loaded. Now you can add additional data or make other map adjustments.
        })
}