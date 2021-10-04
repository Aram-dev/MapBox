package com.aram_dev.mapbox.demo

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.*
import com.aram_dev.mapbox.R
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.MapboxMap.OnMapClickListener
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.*
import com.mapbox.mapboxsdk.style.sources.*
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.HashMap


class SymbolLayerMapillaryActivity : AppCompatActivity(), OnMapReadyCallback,
    OnMapClickListener {
    private var mapView: MapView? = null
    private var mapboxMap: MapboxMap? = null
    private var recyclerView: RecyclerView? = null
    private var source: GeoJsonSource? = null
    private var featureCollection: FeatureCollection? = null
    private var viewMap: HashMap<String, View>? = null
    private var animatorSet: AnimatorSet? = null
    private var loadMapillaryDataTask: LoadMapillaryDataTask? = null

    @ActivityStep
    private var currentStep = 0

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
        STEP_INITIAL, STEP_LOADING, STEP_READY
    )
    annotation class ActivityStep
    companion object {
        private const val SOURCE_ID = "mapbox.poi"
        private const val MAKI_LAYER_ID = "mapbox.poi.maki"
        private const val LOADING_LAYER_ID = "mapbox.poi.loading"
        private const val CALLOUT_LAYER_ID = "mapbox.poi.callout"
        private const val PROPERTY_SELECTED = "selected"
        private const val PROPERTY_LOADING = "loading"
        private const val PROPERTY_LOADING_PROGRESS = "loading_progress"
        private const val PROPERTY_TITLE = "title"
        private const val PROPERTY_FAVOURITE = "favourite"
        private const val PROPERTY_DESCRIPTION = "description"
        private const val PROPERTY_POI = "poi"
        private const val PROPERTY_STYLE = "style"
        private const val CAMERA_ANIMATION_TIME: Long = 1950
        private const val LOADING_CIRCLE_RADIUS = 60f
        private const val LOADING_PROGRESS_STEPS = 25 //number of steps in a progress animation
        private const val LOADING_STEP_DURATION = 50 //duration between each step
        private const val STEP_INITIAL = 0
        private const val STEP_LOADING = 1
        private const val STEP_READY = 2
        private val stepZoomMap: MutableMap<Int, Double> = HashMap()

        init {
            stepZoomMap[STEP_INITIAL] =
                11.0
            stepZoomMap[STEP_LOADING] =
                13.5
            stepZoomMap[STEP_READY] =
                18.0
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        // This contains the MapView in XML and needs to be called after the access token is configured.
        setContentView(R.layout.activity_symbol_layer_mapillary)
        recyclerView = findViewById(R.id.rv_on_top_of_map)

        // Initialize the map view
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.DARK) {
            mapboxMap.uiSettings.isCompassEnabled = false
            mapboxMap.uiSettings.isLogoEnabled = false
            mapboxMap.uiSettings.isAttributionEnabled = false
            LoadPoiDataTask(this@SymbolLayerMapillaryActivity).execute()
            mapboxMap.addOnMapClickListener(this@SymbolLayerMapillaryActivity)
        }
    }

    override fun onMapClick(point: LatLng): Boolean {
        val screenPoint = mapboxMap!!.projection.toScreenLocation(point)
        val features = mapboxMap!!.queryRenderedFeatures(screenPoint, CALLOUT_LAYER_ID)
        if (!features.isEmpty()) {
            // we received a click event on the callout layer
            val feature = features[0]
            val symbolScreenPoint =
                mapboxMap!!.projection.toScreenLocation(convertToLatLng(feature))
            handleClickCallout(feature, screenPoint, symbolScreenPoint)
        } else {
            // we didn't find a click event on callout layer, try clicking maki layer
            return handleClickIcon(screenPoint)
        }
        return true
    }

    fun setupData(collection: FeatureCollection?) {
        if (mapboxMap == null) {
            return
        }
        featureCollection = collection
        mapboxMap!!.getStyle { style ->
            setupSource(style)
            setupMakiLayer(style)
            setupLoadingLayer(style)
            setupCalloutLayer(style)
            setupRecyclerView()
            hideLabelLayers(style)
            setupMapillaryTiles(style)
        }
    }

    private fun setupSource(loadedMapStyle: Style) {
        source = GeoJsonSource(SOURCE_ID, featureCollection)
        loadedMapStyle.addSource(source!!)
    }

    private fun refreshSource() {
        if (source != null && featureCollection != null) {
            source!!.setGeoJson(featureCollection)
        }
    }

    /**
     * Setup a layer with maki icons, eg. restaurant.
     */
    private fun setupMakiLayer(loadedMapStyle: Style) {
        loadedMapStyle.addLayer(
            SymbolLayer(MAKI_LAYER_ID, SOURCE_ID)
                .withProperties( /* show maki icon based on the value of poi feature property
         * https://www.mapbox.com/maki-icons/
         */
                    PropertyFactory.iconImage("{poi}-15"),  /* allows show all icons */
                    PropertyFactory.iconAllowOverlap(true),  /* when feature is in selected state, grow icon */
                    PropertyFactory.iconSize(
                        Expression.match(
                            Expression.toString(
                                Expression.get(
                                    PROPERTY_SELECTED
                                )
                            ), Expression.literal(1.0f),
                            Expression.stop("true", 1.5f)
                        )
                    )
                )
        )
    }

    /**
     * Setup layer indicating that there is an ongoing progress.
     */
    private fun setupLoadingLayer(loadedMapStyle: Style) {
        loadedMapStyle.addLayerBelow(
            CircleLayer(LOADING_LAYER_ID, SOURCE_ID)
                .withProperties(
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.exponential(1), Expression.get(
                                PROPERTY_LOADING_PROGRESS
                            ), *loadingAnimationStops
                        )
                    ),
                    PropertyFactory.circleColor(Color.GRAY),
                    PropertyFactory.circleOpacity(0.6f)
                )
                .withFilter(
                    Expression.eq(
                        Expression.get(
                            PROPERTY_LOADING
                        ), Expression.literal(true)
                    )
                ), MAKI_LAYER_ID
        )
    }

    private val loadingAnimationStops: Array<Expression.Stop>
        private get() {
            val stops: MutableList<Expression.Stop> = ArrayList()
            for (i in 0 until LOADING_PROGRESS_STEPS) {
                stops.add(Expression.stop(i, LOADING_CIRCLE_RADIUS * i / LOADING_PROGRESS_STEPS))
            }
            return stops.toTypedArray()
        }

    /**
     * Setup a layer with Android SDK call-outs
     *
     *
     * title of the feature is used as key for the iconImage
     *
     */
    private fun setupCalloutLayer(loadedMapStyle: Style) {
        loadedMapStyle.addLayer(
            SymbolLayer(CALLOUT_LAYER_ID, SOURCE_ID)
                .withProperties( /* show image with id title based on the value of the title feature property */
                    PropertyFactory.iconImage("{title}"),  /* set anchor of icon to bottom-left */
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM_LEFT),  /* offset icon slightly to match bubble layout */
                    PropertyFactory.iconOffset(arrayOf(-20.0f, -10.0f))
                ) /* add a filter to show only when selected feature property is true */
                .withFilter(
                    Expression.eq(
                        Expression.get(
                            PROPERTY_SELECTED
                        ), Expression.literal(true)
                    )
                )
        )
    }

    private fun setupRecyclerView() {
        val adapter: RecyclerView.Adapter<*> = LocationRecyclerViewAdapter(this, featureCollection)
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView!!.layoutManager = layoutManager
        recyclerView!!.itemAnimator = DefaultItemAnimator()
        recyclerView!!.adapter = adapter
        recyclerView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val index = layoutManager.findFirstVisibleItemPosition()
                    setSelected(index, false)
                }
            }
        })
        val snapHelper: SnapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
    }

    private fun hideLabelLayers(style: Style) {
        var id: String
        for (layer in style.layers) {
            id = layer.id
            if (id.startsWith("place") || id.startsWith("poi") || id.startsWith("marine") || id.startsWith(
                    "road-label"
                )
            ) {
                layer.setProperties(PropertyFactory.visibility(Property.NONE))
            }
        }
    }

    private fun setupMapillaryTiles(loadedMapStyle: Style) {
        loadedMapStyle.addSource(MapillaryTiles.createSource())
        loadedMapStyle.addLayerBelow(MapillaryTiles.createLineLayer(), LOADING_LAYER_ID)
    }

    /**
     * This method handles click events for callout symbols.
     *
     *
     * It creates a hit rectangle based on the the textView, offsets that rectangle to the location
     * of the symbol on screen and hit tests that with the screen point.
     *
     *
     * @param feature           the feature that was clicked
     * @param screenPoint       the point on screen clicked
     * @param symbolScreenPoint the point of the symbol on screen
     */
    private fun handleClickCallout(
        feature: Feature,
        screenPoint: PointF,
        symbolScreenPoint: PointF
    ) {
        val view = viewMap!![feature.getStringProperty(PROPERTY_TITLE)]
        val textContainer = view!!.findViewById<View>(R.id.text_container)

        // create hitbox for textView
        val hitRectText = Rect()
        textContainer.getHitRect(hitRectText)

        // move hitbox to location of symbol
        hitRectText.offset(symbolScreenPoint.x.toInt(), symbolScreenPoint.y.toInt())

        // offset vertically to match anchor behaviour
        hitRectText.offset(0, -view.measuredHeight)

        // hit test if clicked point is in textview hitbox
        if (hitRectText.contains(screenPoint.x.toInt(), screenPoint.y.toInt())) {
            // user clicked on text
            val callout = feature.getStringProperty("call-out")
            Toast.makeText(this, callout, Toast.LENGTH_LONG).show()
        } else {
            // user clicked on icon
            val featureList = featureCollection!!.features()
            for (i in featureList!!.indices) {
                if (featureList[i].getStringProperty(PROPERTY_TITLE) == feature.getStringProperty(
                        PROPERTY_TITLE
                    )
                ) {
                    toggleFavourite(i)
                }
            }
        }
    }

    /**
     * This method handles click events for maki symbols.
     *
     *
     * When a maki symbol is clicked, we moved that feature to the selected state.
     *
     *
     * @param screenPoint the point on screen clicked
     */
    private fun handleClickIcon(screenPoint: PointF): Boolean {
        val features = mapboxMap!!.queryRenderedFeatures(screenPoint, MAKI_LAYER_ID)
        if (!features.isEmpty()) {
            val title = features[0].getStringProperty(PROPERTY_TITLE)
            val featureList = featureCollection!!.features()
            for (i in featureList!!.indices) {
                if (featureList[i].getStringProperty(PROPERTY_TITLE) == title) {
                    setSelected(i, true)
                }
            }
            return true
        }
        return false
    }

    /**
     * Set a feature selected state with the ability to scroll the RecycleViewer to the provided index.
     *
     * @param index      the index of selected feature
     * @param withScroll indicates if the recyclerView position should be updated
     */
    private fun setSelected(index: Int, withScroll: Boolean) {
        if (recyclerView!!.visibility == View.GONE) {
            recyclerView!!.visibility = View.VISIBLE
        }
        deselectAll(false)
        val feature = featureCollection!!.features()!![index]
        selectFeature(feature)
        animateCameraToSelection(feature)
        refreshSource()
        loadMapillaryData(feature)
        if (withScroll) {
            recyclerView!!.scrollToPosition(index)
        }
    }

    /**
     * Deselects the state of all the features
     */
    private fun deselectAll(hideRecycler: Boolean) {
        for (feature in featureCollection!!.features()!!) {
            feature.properties()!!
                .addProperty(PROPERTY_SELECTED, false)
        }
        if (hideRecycler) {
            recyclerView!!.visibility = View.GONE
        }
    }

    /**
     * Selects the state of a feature
     *
     * @param feature the feature to be selected.
     */
    private fun selectFeature(feature: Feature) {
        feature.properties()!!
            .addProperty(PROPERTY_SELECTED, true)
    }

    private val selectedFeature: Feature?
        private get() {
            if (featureCollection != null) {
                for (feature in featureCollection!!.features()!!) {
                    if (feature.getBooleanProperty(PROPERTY_SELECTED)) {
                        return feature
                    }
                }
            }
            return null
        }

    /**
     * Animate camera to a feature.
     *
     * @param feature the feature to animate to
     */
    private fun animateCameraToSelection(feature: Feature?, newZoom: Double) {
        val cameraPosition = mapboxMap!!.cameraPosition
        if (animatorSet != null) {
            animatorSet!!.cancel()
        }
        animatorSet = AnimatorSet()
        animatorSet!!.playTogether(
            createLatLngAnimator(cameraPosition.target, convertToLatLng(feature)),
            createZoomAnimator(cameraPosition.zoom, newZoom),
            createBearingAnimator(
                cameraPosition.bearing,
                feature!!.getNumberProperty("bearing").toDouble()
            ),
            createTiltAnimator(cameraPosition.tilt, feature.getNumberProperty("tilt").toDouble())
        )
        animatorSet!!.start()
    }

    private fun animateCameraToSelection(feature: Feature) {
        val zoom = feature.getNumberProperty("zoom").toDouble()
        animateCameraToSelection(feature, zoom)
    }

    private fun loadMapillaryData(feature: Feature) {
        if (loadMapillaryDataTask != null) {
            loadMapillaryDataTask!!.cancel(true)
        }
        loadMapillaryDataTask = LoadMapillaryDataTask(
            this,
            mapboxMap, Picasso.Builder(applicationContext).build(), Handler(), feature
        )
        loadMapillaryDataTask!!.execute(50)
    }

    /**
     * Set the favourite state of a feature based on the index.
     *
     * @param index the index of the feature to favourite/de-favourite
     */
    private fun toggleFavourite(index: Int) {
        val feature = featureCollection!!.features()!![index]
        val title = feature.getStringProperty(PROPERTY_TITLE)
        val currentState = feature.getBooleanProperty(PROPERTY_FAVOURITE)
        feature.properties()!!
            .addProperty(PROPERTY_FAVOURITE, !currentState)
        val view = viewMap!![title]
        val imageView = view!!.findViewById<ImageView>(R.id.logoView)
        imageView.setImageResource(if (currentState) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        val bitmap = SymbolGenerator.generate(view)
        mapboxMap!!.getStyle { style ->
            style.addImage(title, bitmap)
            refreshSource()
        }
    }

    /**
     * Invoked when the bitmaps have been generated from a view.
     */
    fun setImageGenResults(viewMap: HashMap<String, View>?, imageMap: HashMap<String, Bitmap>?) {
        mapboxMap!!.getStyle { style -> // calling addImages is faster as separate addImage calls for each bitmap.
            style.addImages(imageMap!!)
        }
        // need to store reference to views to be able to use them as hitboxes for click events.
        this@SymbolLayerMapillaryActivity.viewMap = viewMap
    }

    private fun setActivityStep(@ActivityStep activityStep: Int) {
        val selectedFeature = selectedFeature
        val zoom = stepZoomMap[activityStep]!!
        animateCameraToSelection(selectedFeature, zoom)
        currentStep = activityStep
    }

    override fun onStart() {
        super.onStart()
        mapView!!.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView!!.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView!!.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (loadMapillaryDataTask != null) {
            loadMapillaryDataTask!!.cancel(true)
        }
        mapView!!.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView!!.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mapboxMap != null) {
            mapboxMap!!.removeOnMapClickListener(this)
        }
        mapView!!.onDestroy()
    }

    override fun onBackPressed() {
        if (currentStep == STEP_LOADING || currentStep == STEP_READY) {
            if (loadMapillaryDataTask != null) {
                loadMapillaryDataTask!!.cancel(true)
            }
            setActivityStep(STEP_INITIAL)
            deselectAll(true)
            refreshSource()
        } else {
            super.onBackPressed()
        }
    }

    private fun convertToLatLng(feature: Feature?): LatLng {
        val symbolPoint = feature!!.geometry() as Point?
        return LatLng(symbolPoint!!.latitude(), symbolPoint.longitude())
    }

    private fun createLatLngAnimator(currentPosition: LatLng, targetPosition: LatLng): Animator {
        val latLngAnimator =
            ValueAnimator.ofObject(LatLngEvaluator(), currentPosition, targetPosition)
        latLngAnimator.duration = CAMERA_ANIMATION_TIME
        latLngAnimator.interpolator = FastOutSlowInInterpolator()
        latLngAnimator.addUpdateListener { animation ->
            mapboxMap!!.moveCamera(
                CameraUpdateFactory.newLatLng(
                    (animation.animatedValue as LatLng)
                )
            )
        }
        return latLngAnimator
    }

    private fun createZoomAnimator(currentZoom: Double, targetZoom: Double): Animator {
        val zoomAnimator = ValueAnimator.ofFloat(
            currentZoom.toFloat(),
            targetZoom.toFloat()
        )
        zoomAnimator.duration = CAMERA_ANIMATION_TIME
        zoomAnimator.interpolator = FastOutSlowInInterpolator()
        zoomAnimator.addUpdateListener { animation ->
            mapboxMap!!.moveCamera(CameraUpdateFactory.zoomTo(animation.animatedValue as Double))
        }
        return zoomAnimator
    }

    private fun createBearingAnimator(currentBearing: Double, targetBearing: Double): Animator {
        val bearingAnimator = ValueAnimator.ofFloat(
            currentBearing.toFloat(),
            targetBearing.toFloat()
        )
        bearingAnimator.duration = CAMERA_ANIMATION_TIME
        bearingAnimator.interpolator = FastOutSlowInInterpolator()
        bearingAnimator.addUpdateListener { animation ->
            mapboxMap!!.moveCamera(CameraUpdateFactory.bearingTo(animation.animatedValue as Double))
        }
        return bearingAnimator
    }

    private fun createTiltAnimator(currentTilt: Double, targetTilt: Double): Animator {
        val tiltAnimator = ValueAnimator.ofFloat(
            currentTilt.toFloat(),
            targetTilt.toFloat()
        )
        tiltAnimator.duration = CAMERA_ANIMATION_TIME
        tiltAnimator.interpolator = FastOutSlowInInterpolator()
        tiltAnimator.addUpdateListener { animation ->
            mapboxMap!!.moveCamera(CameraUpdateFactory.tiltTo(animation.animatedValue as Double))
        }
        return tiltAnimator
    }

    /**
     * Helper class to evaluate LatLng objects with a ValueAnimator
     */
    private class LatLngEvaluator : TypeEvaluator<LatLng> {
        private val latLng = LatLng()
        override fun evaluate(fraction: Float, startValue: LatLng, endValue: LatLng): LatLng {
            latLng.latitude = (startValue.latitude
                    + (endValue.latitude - startValue.latitude) * fraction)
            latLng.longitude = (startValue.longitude
                    + (endValue.longitude - startValue.longitude) * fraction)
            return latLng
        }
    }

    /**
     * AsyncTask to load data from the assets folder.
     */
    private class LoadPoiDataTask internal constructor(activity: SymbolLayerMapillaryActivity) :
        AsyncTask<Void?, Void?, FeatureCollection?>() {
        private val activityRef: WeakReference<SymbolLayerMapillaryActivity> =
            WeakReference(activity)

        override fun doInBackground(vararg params: Void?): FeatureCollection? {
            val activity = activityRef.get() ?: return null
            val geoJson = loadGeoJsonFromAsset(activity, "sf_poi.geojson")
            return FeatureCollection.fromJson(geoJson)
        }

        override fun onPostExecute(featureCollection: FeatureCollection?) {
            super.onPostExecute(featureCollection)
            val activity = activityRef.get()
            if (featureCollection == null || activity == null) {
                return
            }
            activity.setupData(featureCollection)
            GenerateViewIconTask(activity).execute(featureCollection)
        }

        companion object {
            fun loadGeoJsonFromAsset(context: Context, filename: String?): String {
                return try {
                    // Load GeoJSON file from local asset folder
                    val `is` = context.assets.open(filename!!)
                    val size = `is`.available()
                    val buffer = ByteArray(size)
                    `is`.read(buffer)
                    `is`.close()
                    String(buffer, Charset.forName("UTF-8"))
                } catch (exception: Exception) {
                    throw RuntimeException(exception)
                }
            }
        }

    }

    /**
     * AsyncTask to generate Bitmap from Views to be used as iconImage in a SymbolLayer.
     *
     *
     * Call be optionally be called to update the underlying data source after execution.
     *
     *
     *
     * Generating Views on background thread since we are not going to be adding them to the view hierarchy.
     *
     */
    private open class GenerateViewIconTask @JvmOverloads internal constructor(
        activity: SymbolLayerMapillaryActivity,
        refreshSource: Boolean = false
    ) :
        AsyncTask<FeatureCollection?, Void?, HashMap<String, Bitmap>?>() {
        private val viewMap = HashMap<String, View>()
        private val activityRef: WeakReference<SymbolLayerMapillaryActivity> =
            WeakReference(activity)
        private val refreshSource: Boolean = refreshSource
        override fun doInBackground(vararg params: FeatureCollection?): HashMap<String, Bitmap>? {
            val activity = activityRef.get()
            return if (activity != null) {
                val imagesMap = HashMap<String, Bitmap>()
                val inflater = LayoutInflater.from(activity)
                val featureCollection = params[0]
                for (feature in featureCollection?.features()!!) {
                    val view: View = inflater.inflate(R.layout.mapillary_layout_callout, null)
                    val name = feature.getStringProperty(PROPERTY_TITLE)
                    val titleTv = view.findViewById<TextView>(R.id.title)
                    titleTv.text = name
                    val style = feature.getStringProperty(PROPERTY_STYLE)
                    val styleTv = view.findViewById<TextView>(R.id.style)
                    styleTv.text = style
                    val favourite = feature.getBooleanProperty(PROPERTY_FAVOURITE)
                    val imageView = view.findViewById<ImageView>(R.id.logoView)
                    imageView.setImageResource(if (favourite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
                    val bitmap = SymbolGenerator.generate(view)
                    imagesMap[name] = bitmap
                    viewMap[name] = view
                }
                imagesMap
            } else {
                null
            }
        }

        override fun onPostExecute(bitmapHashMap: HashMap<String, Bitmap>?) {
            super.onPostExecute(bitmapHashMap)
            val activity = activityRef.get()
            if (activity != null && bitmapHashMap != null) {
                activity.setImageGenResults(viewMap, bitmapHashMap)
                if (refreshSource) {
                    activity.refreshSource()
                }
            }
        }

    }

    /**
     * Async task which fetches pictures from around the POI using Mapillary services.
     * https://www.mapillary.com/developer/api-documentation/
     */
    private class LoadMapillaryDataTask(
        activity: SymbolLayerMapillaryActivity, map: MapboxMap?, picasso: Picasso,
        progressHandler: Handler, feature: Feature
    ) : AsyncTask<Int?, Void?, MapillaryDataLoadResult?>() {
        private val activityRef: WeakReference<SymbolLayerMapillaryActivity> =
            WeakReference(activity)
        private val map: MapboxMap?
        private val picasso: Picasso
        private val progressHandler: Handler
        private var loadingProgress = 0
        private var loadingIncrease = true
        private val feature: Feature
        override fun onPreExecute() {
            super.onPreExecute()
            loadingProgress = 0
            setLoadingState(true, false)
        }

        override fun doInBackground(vararg radius: Int?): MapillaryDataLoadResult? {
            progressHandler.post(progressRunnable)
            try {
                Thread.sleep(2500) //ensure loading visualisation
            } catch (exception: InterruptedException) {
                exception.printStackTrace()
            }
            val okHttpClient = OkHttpClient()
            try {
                val poiPosition = feature.geometry() as Point?
                @SuppressLint("DefaultLocale") val request = Request.Builder()
                    .url(
                        String.format(
                            API_URL,
                            poiPosition!!.longitude(), poiPosition.latitude(),
                            poiPosition.longitude(), poiPosition.latitude(),
                            radius[0]
                        )
                    )
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val featureCollection = FeatureCollection.fromJson(
                    response.body()!!.string()
                )
                val mapillaryDataLoadResult = MapillaryDataLoadResult(featureCollection)
                for (feature in featureCollection.features()!!) {
                    val imageId = feature.getStringProperty(KEY_UNIQUE_FEATURE)
                    val imageUrl = String.format(URL_IMAGE_PLACEHOLDER, imageId)
                    var bitmap: Bitmap = picasso.load(imageUrl).resize(IMAGE_SIZE, IMAGE_SIZE).get()

                    //cropping bitmap to be circular
                    bitmap = getCroppedBitmap(bitmap)
                    mapillaryDataLoadResult.add(feature, bitmap)
                }
                return mapillaryDataLoadResult
            } catch (exception: Exception) {
                Timber.e(exception)
            }
            return null
        }

        override fun onPostExecute(mapillaryDataLoadResult: MapillaryDataLoadResult?) {
            super.onPostExecute(mapillaryDataLoadResult)
            setLoadingState(false, true)
            if (mapillaryDataLoadResult == null) {
                val activity = activityRef.get()
                if (activity != null) {
                    Toast.makeText(
                        activity,
                        "Error. Unable to load Mapillary data.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            val featureCollection = mapillaryDataLoadResult.mapillaryFeatureCollection
            val bitmapMap: Map<Feature, Bitmap> = mapillaryDataLoadResult.bitmapHashMap
            for ((feature, value) in bitmapMap) {
                val key = feature.getStringProperty(KEY_UNIQUE_FEATURE)
                map!!.style!!.addImage(key, value)
            }
            val mapillarySource = map!!.style!!.getSource(ID_SOURCE) as GeoJsonSource?
            if (mapillarySource == null) {
                map.style!!.addSource(
                    GeoJsonSource(
                        ID_SOURCE, featureCollection, GeoJsonOptions()
                            .withCluster(true)
                            .withClusterMaxZoom(17)
                            .withClusterRadius(IMAGE_SIZE / 3)
                    )
                )

                // unclustered
                map.style!!.addLayerBelow(
                    SymbolLayer(ID_LAYER_UNCLUSTERED, ID_SOURCE).withProperties(
                        PropertyFactory.iconImage(TOKEN_UNIQUE_FEATURE),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconSize(
                            Expression.interpolate(
                                Expression.exponential(1f), Expression.zoom(),
                                Expression.stop(12, 0.0f),
                                Expression.stop(15, 0.8f),
                                Expression.stop(16, 1.1f),
                                Expression.stop(17, 1.4f),
                                Expression.stop(18, 1.7f)
                            )
                        )
                    ), MAKI_LAYER_ID
                )

                // clustered
                val layers = arrayOf(
                    intArrayOf(20, Color.RED),
                    intArrayOf(10, Color.BLUE),
                    intArrayOf(0, Color.GREEN)
                )
                for (i in layers.indices) {
                    val pointCount = Expression.toNumber(Expression.get("point_count"))

                    //Add cluster circles
                    val clusterLayer = CircleLayer("cluster-$i", ID_SOURCE)
                    clusterLayer.setProperties(
                        PropertyFactory.circleColor(layers[i][1]),
                        PropertyFactory.circleRadius(
                            Expression.interpolate(
                                Expression.exponential(1f),
                                Expression.zoom(),
                                Expression.stop(12, 10f),
                                Expression.stop(14, 16f),
                                Expression.stop(15, 18f),
                                Expression.stop(16, 20f)
                            )
                        ),
                        PropertyFactory.circleOpacity(0.6f)
                    )
                    clusterLayer.maxZoom = 17f

                    // Add a filter to the cluster layer that hides the circles based on "point_count"
                    clusterLayer.setFilter(
                        if (i == 0) Expression.gte(
                            pointCount, Expression.literal(
                                layers[i][0]
                            )
                        ) else Expression.all(
                            Expression.gte(
                                pointCount, Expression.literal(
                                    layers[i][0]
                                )
                            ),
                            Expression.lt(
                                pointCount, Expression.literal(
                                    layers[i - 1][0]
                                )
                            )
                        )
                    )
                    map.style!!.addLayerBelow(clusterLayer, MAKI_LAYER_ID)
                }

                //Add the count labels
                val count = SymbolLayer("count", ID_SOURCE)
                count.setProperties(
                    PropertyFactory.textField("{point_count}"),
                    PropertyFactory.textSize(8f),
                    PropertyFactory.textOffset(arrayOf(0.0f, 0.0f)),
                    PropertyFactory.textColor(Color.WHITE),
                    PropertyFactory.textIgnorePlacement(true)
                )
                map.style!!.addLayerBelow(count, MAKI_LAYER_ID)
            } else {
                mapillarySource.setGeoJson(featureCollection)
            }
        }

        private val progressRunnable: Runnable = object : Runnable {
            override fun run() {
                if (isCancelled) {
                    setLoadingState(false, false)
                    return
                }
                if (loadingIncrease) {
                    if (loadingProgress >= LOADING_PROGRESS_STEPS) {
                        loadingIncrease = false
                    }
                } else {
                    if (loadingProgress <= 0) {
                        loadingIncrease = true
                    }
                }
                loadingProgress = if (loadingIncrease) loadingProgress + 1 else loadingProgress - 1
                feature.addNumberProperty(PROPERTY_LOADING_PROGRESS, loadingProgress)
                val activity = activityRef.get()
                activity?.refreshSource()
                progressHandler.postDelayed(this, LOADING_STEP_DURATION.toLong())
            }
        }

        private fun setLoadingState(isLoading: Boolean, isSuccess: Boolean) {
            progressHandler.removeCallbacksAndMessages(null)
            feature.addBooleanProperty(PROPERTY_LOADING, isLoading)
            val activity = activityRef.get()
            if (activity != null) {
                activity.refreshSource()
                if (isLoading) { //zooming to a loading state
                    activity.setActivityStep(STEP_LOADING)
                } else if (isSuccess) { //if success zooming to a ready state, otherwise do nothing
                    activity.setActivityStep(STEP_READY)
                }
            }
        }

        companion object {
            const val URL_IMAGE_PLACEHOLDER =
                "https://d1cuyjsrcm0gby.cloudfront.net/%s/thumb-320.jpg"
            const val KEY_UNIQUE_FEATURE = "key"
            const val TOKEN_UNIQUE_FEATURE = "{" + KEY_UNIQUE_FEATURE + "}"
            const val ID_SOURCE = "cluster_source"
            const val ID_LAYER_UNCLUSTERED = "unclustered_layer"
            const val IMAGE_SIZE = 128
            const val API_URL = ("https://a.mapillary.com/v3/images/"
                    + "?lookat=%f,%f&closeto=%f,%f&radius=%d"
                    + "&client_id=bjgtc1FDTnFPaXpxeTZuUDNabmJ5dzozOGE1ODhkMmEyYTkyZTI4")

            fun getCroppedBitmap(bitmap: Bitmap): Bitmap {
                val output = Bitmap.createBitmap(
                    bitmap.width,
                    bitmap.height, Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(output)
                val color = -0xbdbdbe
                val paint = Paint()
                val rect = Rect(0, 0, bitmap.width, bitmap.height)
                paint.isAntiAlias = true
                canvas.drawARGB(0, 0, 0, 0)
                paint.color = color
                // canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
                canvas.drawCircle(
                    bitmap.width.toFloat() / 2, bitmap.height.toFloat() / 2,
                    bitmap.width.toFloat() / 2, paint
                )
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(bitmap, rect, rect, paint)
                //Bitmap _bmp = Bitmap.createScaledBitmap(output, 60, 60, false);
                //return _bmp;
                return output
            }
        }

        init {
            this.map = map
            this.picasso = picasso
            this.progressHandler = progressHandler
            this.feature = feature
        }
    }

    private class MapillaryDataLoadResult internal constructor(val mapillaryFeatureCollection: FeatureCollection) {
        val bitmapHashMap = HashMap<Feature, Bitmap>()
        fun add(feature: Feature, bitmap: Bitmap) {
            bitmapHashMap[feature] = bitmap
        }
    }

    /**
     * Utility class to generate Bitmaps for Symbol.
     */
    private object SymbolGenerator {
        /**
         * Generate a Bitmap from an Android SDK View.
         *
         * @param view the View to be drawn to a Bitmap
         * @return the generated bitmap
         */
        fun generate(view: View): Bitmap {
            val measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(measureSpec, measureSpec)
            val measuredWidth = view.measuredWidth
            val measuredHeight = view.measuredHeight
            view.layout(0, 0, measuredWidth, measuredHeight)
            val bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            return bitmap
        }
    }

    /**
     * Util class that creates a Source and a Layer based on Mapillary data.
     * https://www.mapillary.com/developer/tiles-documentation/
     */
    private object MapillaryTiles {
        const val ID_SOURCE = "mapillary.source"
        const val ID_LINE_LAYER = "mapillary.layer.line"
        const val URL_TILESET = "https://d25uarhxywzl1j.cloudfront.net/v0.1/{z}/{x}/{y}.mvt"
        fun createSource(): Source {
            val mapillaryTileset = TileSet("2.1.0", URL_TILESET)
            mapillaryTileset.minZoom = 0f
            mapillaryTileset.maxZoom = 14f
            return VectorSource(ID_SOURCE, mapillaryTileset)
        }

        fun createLineLayer(): Layer {
            val lineLayer = LineLayer(ID_LINE_LAYER, ID_SOURCE)
            lineLayer.setSourceLayer("mapillary-sequences")
            lineLayer.setProperties(
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineWidth(2.0f),
                PropertyFactory.lineColor(Color.GREEN)
            )
            return lineLayer
        }
    }

    /**
     * RecyclerViewAdapter adapting features to cards.
     */
    internal class LocationRecyclerViewAdapter(
        private val activity: SymbolLayerMapillaryActivity?,
        featureCollection: FeatureCollection?
    ) :
        RecyclerView.Adapter<LocationRecyclerViewAdapter.MyViewHolder>() {
        val featureCollection: List<Feature>? = featureCollection!!.features()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            val itemView: View = LayoutInflater.from(parent.context)
                .inflate(R.layout.cardview_symbol_layer, parent, false)
            return MyViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            val feature = featureCollection!![position]
            holder.title.text = feature.getStringProperty(PROPERTY_TITLE)
            holder.description.text = feature.getStringProperty(PROPERTY_DESCRIPTION)
            holder.poi.text = feature.getStringProperty(PROPERTY_POI)
            holder.style.text = feature.getStringProperty(PROPERTY_STYLE)
            holder.setHolderClickListener(object : ItemClickListener {
                override fun onClick(view: View?, position: Int) {
                    activity?.toggleFavourite(position)
                }
            })
        }

        override fun getItemCount(): Int {
            return featureCollection!!.size
        }

        /**
         * ViewHolder for RecyclerView.
         */
        internal class MyViewHolder(view: View) : RecyclerView.ViewHolder(view),
            View.OnClickListener {
            var title: TextView
            var poi: TextView
            var style: TextView
            var description: TextView
            var singleCard: CardView
            var clickListener: ItemClickListener? = null
            fun setHolderClickListener(itemClickListener: ItemClickListener?) {
                clickListener = itemClickListener
            }

            override fun onClick(view: View) {
                clickListener!!.onClick(view, layoutPosition)
            }

            init {
                title = view.findViewById(R.id.textview_title)
                poi = view.findViewById(R.id.textview_poi)
                style = view.findViewById(R.id.textview_style)
                description = view.findViewById(R.id.textview_description)
                singleCard = view.findViewById(R.id.single_location_cardview)
                singleCard.setOnClickListener(this)
            }
        }

    }

    internal interface ItemClickListener {
        fun onClick(view: View?, position: Int)
    }
}
