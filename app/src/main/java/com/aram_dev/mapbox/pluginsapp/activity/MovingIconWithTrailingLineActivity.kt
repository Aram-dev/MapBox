package com.aram_dev.mapbox.pluginsapp.activity
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aram_dev.mapbox.R
import com.aram_dev.mapbox.databinding.ActivityMovingIconWithTrailingLineBinding
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.core.constants.Constants
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point


import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.turf.TurfMeasurement
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import java.util.ArrayList


/**
 * Make a directions request with the Mapbox Directions API and then draw a line behind a moving
 * SymbolLayer icon which moves along the Directions response route.
 */
class MovingIconWithTrailingLineActivity : AppCompatActivity() {
    private var mapView: MapView? = null
    private var mapboxMap: MapboxMap? = null
    private var pointSource: GeoJsonSource? = null
    private var lineSource: GeoJsonSource? = null
    private var routeCoordinateList: List<Point>? = null
    private val markerLinePointList: MutableList<Point> = ArrayList()
    private var routeIndex = 0
    private val originPoint = Point.fromLngLat(38.7508, 9.0309)
    private val destinationPoint = Point.fromLngLat(38.795902, 8.984467)
    private var currentAnimator: Animator? = null
    private var binding: ActivityMovingIconWithTrailingLineBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        binding = ActivityMovingIconWithTrailingLineBinding.inflate(layoutInflater)

        // This contains the MapView in XML and needs to be called after the access token is configured.
        setContentView(binding?.root)

        // Initialize the mapboxMap view
        mapView = binding?.mapView
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(OnMapReadyCallback { mapboxMap ->
            this@MovingIconWithTrailingLineActivity.mapboxMap = mapboxMap
            mapboxMap.setStyle(Style.LIGHT) { // Use the Mapbox Directions API to get a directions route
                getRoute(originPoint, destinationPoint)
            }
        })
    }

    /**
     * Add data to the map once the GeoJSON has been loaded
     *
     * @param featureCollection returned GeoJSON FeatureCollection from the Directions API route request
     */
    private fun initData(fullyLoadedStyle: Style, featureCollection: FeatureCollection) {
        if (featureCollection.features() != null) {
            val lineString = featureCollection.features()!![0].geometry() as LineString?
            if (lineString != null) {
                routeCoordinateList = lineString.coordinates()
                initSources(fullyLoadedStyle, featureCollection)
                initSymbolLayer(fullyLoadedStyle)
                initDotLinePath(fullyLoadedStyle)
                animate()
            }
        }
    }

    /**
     * Set up the repeat logic for moving the icon along the route.
     */
    private fun animate() {
        // Check if we are at the end of the points list
        if (routeCoordinateList!!.size - 1 > routeIndex) {
            val indexPoint = routeCoordinateList!![routeIndex]
            val newPoint = Point.fromLngLat(indexPoint.longitude(), indexPoint.latitude())
            currentAnimator = createLatLngAnimator(indexPoint, newPoint)
            currentAnimator!!.start()
            routeIndex++
        }
    }

    private class PointEvaluator : TypeEvaluator<Point> {
        override fun evaluate(fraction: Float, startValue: Point, endValue: Point): Point {
            return Point.fromLngLat(
                startValue.longitude() + (endValue.longitude() - startValue.longitude()) * fraction,
                startValue.latitude() + (endValue.latitude() - startValue.latitude()) * fraction
            )
        }
    }

    private fun createLatLngAnimator(currentPosition: Point, targetPosition: Point): Animator {
        val latLngAnimator =
            ValueAnimator.ofObject(PointEvaluator(), currentPosition, targetPosition)
        latLngAnimator.duration =
            TurfMeasurement.distance(currentPosition, targetPosition, "meters")
                .toLong()
        latLngAnimator.interpolator = LinearInterpolator()
        latLngAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                animate()
            }
        })
        latLngAnimator.addUpdateListener { animation ->
            val point = animation.animatedValue as Point
            pointSource!!.setGeoJson(point)
            markerLinePointList.add(point)
            lineSource!!.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(
                markerLinePointList)))
        }
        return latLngAnimator
    }

    /**
     * Make a request to the Mapbox Directions API. Once successful, pass the route to the
     * route layer.
     *
     * @param origin      the starting point of the route
     * @param destination the desired finish point of the route
     */
    private fun getRoute(origin: Point, destination: Point) {
        val client = MapboxDirections.builder()
            .origin(origin)
            .destination(destination)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(DirectionsCriteria.PROFILE_WALKING)
            .accessToken(getString(R.string.mapbox_access_token))
            .build()
        client.enqueueCall(object : Callback<DirectionsResponse?> {
            override fun onResponse(
                call: Call<DirectionsResponse?>,
                response: Response<DirectionsResponse?>,
            ) {
                println(call.request().url().toString())

                // You can get the generic HTTP info about the response
                Timber.d("Response code: %s", response.code())
                if (response.body() == null) {
                    Timber.e("No routes found, make sure you set the right user and access token.")
                    return
                } else if (response.body()!!.routes().size < 1) {
                    Timber.e("No routes found")
                    return
                }

                // Get the directions route
                val currentRoute = response.body()!!.routes()[0]
                mapboxMap!!.getStyle { style ->
                    mapboxMap!!.easeCamera(CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder()
                            .include(LatLng(origin.latitude(), origin.longitude()))
                            .include(LatLng(destination.latitude(), destination.longitude()))
                            .build(), 50), 5000)
                    initData(style, FeatureCollection.fromFeature(
                        Feature.fromGeometry(LineString.fromPolyline(currentRoute.geometry()!!,
                            Constants.PRECISION_6))))
                }
            }

            override fun onFailure(call: Call<DirectionsResponse?>, throwable: Throwable) {
                Timber.e("Error: %s", throwable.message)
                Toast.makeText(this@MovingIconWithTrailingLineActivity,
                    "Error: " + throwable.message,
                    Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Add various sources to the map.
     */
    private fun initSources(loadedMapStyle: Style, featureCollection: FeatureCollection) {
        loadedMapStyle.addSource(GeoJsonSource(DOT_SOURCE_ID, featureCollection).also {
            pointSource = it
        })
        loadedMapStyle.addSource(GeoJsonSource(LINE_SOURCE_ID).also {
            lineSource = it
        })
    }

    /**
     * Add the marker icon SymbolLayer.
     */
    private fun initSymbolLayer(loadedMapStyle: Style) {
        loadedMapStyle.addImage("moving-red-marker", BitmapFactory.decodeResource(
            resources, R.drawable.mapbox_ic_start_navigation))
        loadedMapStyle.addLayer(SymbolLayer("symbol-layer-id", DOT_SOURCE_ID).withProperties(
            PropertyFactory.iconImage("moving-red-marker"),
            PropertyFactory.iconSize(1f),
            PropertyFactory.iconOffset(arrayOf(5f, 0f)),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAllowOverlap(true)
        ))
    }

    /**
     * Add the LineLayer for the marker icon's travel route. Adding it under the "road-label" layer, so that the
     * this LineLayer doesn't block the street name.
     */
    private fun initDotLinePath(loadedMapStyle: Style) {
        loadedMapStyle.addLayerBelow(LineLayer("line-layer-id", LINE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(Color.parseColor("#F13C6E")),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(4f)), "road-label")
    }

    public override fun onResume() {
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

    public override fun onPause() {
        super.onPause()
        mapView!!.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentAnimator != null) {
            currentAnimator!!.cancel()
        }
        mapView!!.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView!!.onSaveInstanceState(outState)
    }

    companion object {
        private const val DOT_SOURCE_ID = "dot-source-id"
        private const val LINE_SOURCE_ID = "line-source-id"
    }
}

