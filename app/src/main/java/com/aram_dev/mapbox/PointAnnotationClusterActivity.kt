package com.aram_dev.mapbox

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aram_dev.mapbox.databinding.ActivityAnnotationBinding
import com.mapbox.android.telemetry.provider.MapboxTelemetryInitProvider
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.expressions.Expression.color
import com.mapbox.mapboxsdk.style.expressions.Expression.literal
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Example showing how to add Symbol cluster annotations
 */
class PointAnnotationClusterActivity : AppCompatActivity(), CoroutineScope {
    private var mapboxMap: MapboxMap? = null
    private val job = Job()
    override val coroutineContext = job + Dispatchers.IO
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var options: List<PointAnnotationOptions>? = null
    private var index: Int = 0
    private val nextStyle: String
        get() {
            return AnnotationUtils.STYLES[index++ % AnnotationUtils.STYLES.size]
        }
    private lateinit var binding: ActivityAnnotationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnotationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.progress.visibility = View.VISIBLE
        binding.mapView.getMapAsync {
            mapboxMap = it

            it.cameraPosition = CameraPosition.Builder().target(LatLng(LONGITUDE, LATITUDE)).zoom(10.0).build()
                CameraOptions.Builder()
                    .center(Point.fromLngLat(LONGITUDE, LATITUDE))
                    .zoom(10.0)
                    .build()

//            loadStyleUri(nextStyle) {
////            it.setStyle(nextStyle) {
//                val annotationPlugin = binding.mapView.annotations
//                val annotationConfig = AnnotationConfig(
//                    annotationSourceOptions = AnnotationSourceOptions(
//                        clusterOptions = ClusterOptions(
//                            textColorExpression = color(Color.YELLOW),
//                            textColor = Color.BLACK, // Will not be applied as textColorExpression has been set
//                            textSize = 20.0,
//                            circleRadiusExpression = literal(25),
//                            colorLevels = listOf(
//                                Pair(100, Color.RED),
//                                Pair(50, Color.BLUE),
//                                Pair(0, Color.GREEN)
//                            )
//                        )
//                    )
//                )
//                pointAnnotationManager =
//                    annotationPlugin.createPointAnnotationManager(binding.mapView, annotationConfig)
//                pointAnnotationManager?.addClickListener(
//                    OnPointAnnotationClickListener { point ->
//                        Toast.makeText(
//                            this@PointAnnotationClusterActivity,
//                            "Click: ${point.id}",
//                            Toast.LENGTH_SHORT
//                        )
//                            .show()
//                        true
//                    }
//                )
//                launch {
//                    loadData()
//                }
//            }
        }

        binding.deleteAll.setOnClickListener { pointAnnotationManager?.deleteAll() }
        binding.changeStyle.setOnClickListener {
            binding.mapView.getMapAsync {
                it.setStyle(nextStyle)
            }
        }

    }

    private fun loadData() {
        AnnotationUtils.loadStringFromNet(this@PointAnnotationClusterActivity, POINTS_URL)?.let {
            FeatureCollection.fromJson(it).features()?.let { features ->
                features.shuffle()
                options = features.take(AMOUNT).map { feature ->
                    PointAnnotationOptions()
                        .withGeometry((feature.geometry() as Point))
                        .withIconImage(ICON_FIRE_STATION)
                }
            }
        }
        runOnUiThread {
            options?.let {
                pointAnnotationManager?.create(it)
            }
            binding.progress.visibility = View.GONE
        }
    }

    companion object {
        private const val AMOUNT = 10000
        private const val ICON_FIRE_STATION = "fire-station-11"
        private const val LONGITUDE = -77.00897
        private const val LATITUDE = 38.87031
        private const val POINTS_URL =
            "https://opendata.arcgis.com/datasets/01d0ff375695466d93d1fa2a976e2bdd_5.geojson"
    }
}