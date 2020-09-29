package com.neotreks.accuterra.accuterraofflinemaps

//
//  OfflinePackExample
//  AccuTerraOfflineMaps
//
//  Created by Rudolf Kopřiva on 23/09/2020.
//
// Example of creating offline cache for AccuTerra maps using Mapbox SDK

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.offline.*
import kotlinx.android.synthetic.main.activity_offlinepackexample.*
import org.json.JSONObject
import kotlin.math.max

class OfflinePackExample : AppCompatActivity(), View.OnClickListener {

    private val TAG = "Demo"
    private var offlinePacksCounter = 0
    private lateinit var offlineManager: OfflineManager
    private var mapboxMap: MapboxMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_offlinepackexample)

        val accuTerraStyleURL = BuildConfig.ACCUTERRA_MAP_STYLE_URL
        require(!accuTerraStyleURL.isNullOrBlank()) { "ACCUTERRA_MAP_STYLE_URL not set in build.gradle" }

        val accuTerraMapAPIKey = BuildConfig.ACCUTERRA_MAP_API_KEY
        require(!accuTerraMapAPIKey.isNullOrBlank()) { "ACCUTERRA_MAP_API_KEY not set in build.gradle" }

        val styleURL = "$accuTerraStyleURL?key=$accuTerraMapAPIKey"

        cacheButton.visibility = View.GONE
        progressBar.visibility = View.GONE

        mapView.onCreate(savedInstanceState)

        offlineManager = OfflineManager.getInstance(applicationContext)

        // Because we are not caching Mapbox tiles, we can increase offline cache limit
        offlineManager.setOfflineMapboxTileCountLimit(Long.MAX_VALUE)

        mapView.getMapAsync {
            mapboxMap = it

            // Set AccuTerra style
            it.setStyle(styleURL) {

                // Set default location for this demo (Castle Rock CO)
                val cameraPosition = CameraPosition.Builder()
                    .target(LatLng(39.38,-104.86))
                    .zoom(13.0).build()

                mapboxMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition),100)

                mapFinishedLoading()
            }
        }
        cacheButton.setOnClickListener(this)
    }

    private fun mapFinishedLoading() {
        Log.d(TAG,"Map finished loading")
        cacheButton.visibility = View.VISIBLE
        listOfflinePacks()
    }

    private fun listOfflinePacks() {
        offlineManager.listOfflineRegions(object: OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<out OfflineRegion>?) {
                this@OfflinePackExample.offlinePacksCounter = offlineRegions?.size ?: 0
                offlineRegions?.forEach {
                    val metadata = it.metadata
                    val jsonString = String(metadata)
                    val jsonObject = JSONObject(jsonString)
                    val name = jsonObject.get("name") ?: "unknown"
                    val style = it.definition.styleURL
                    val minx = jsonObject.get("minx") as? Double ?: 0
                    val miny = jsonObject.get("miny") as? Double ?: 0
                    val maxx = jsonObject.get("maxx") as? Double ?: 0
                    val maxy = jsonObject.get("maxy") as? Double ?: 0

                    Log.d(TAG,"Offline Pack: $name, style: $style, minx: $minx, miny: $miny, maxx: $maxx, maxy: $maxy")
                }
            }

            override fun onError(error: String?) {
                Log.d(TAG,"Could not load list of offline packs $error")
            }
        })
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onClick(v: View?) {
        if (v == cacheButton) {
            if (cacheButton.isSelected) {
                offlineManager.listOfflineRegions(object: OfflineManager.ListOfflineRegionsCallback {

                    override fun onList(offlineRegions: Array<out OfflineRegion>?) {
                        this@OfflinePackExample.cacheButton.visibility = View.VISIBLE
                        this@OfflinePackExample.offlinePacksCounter = offlineRegions?.size ?: 0

                        offlineRegions?.forEach {
                            it.getStatus(object: OfflineRegion.OfflineRegionStatusCallback {

                                override fun onStatus(status: OfflineRegionStatus?) {
                                    if (status?.downloadState == OfflineRegion.STATE_ACTIVE) {
                                        val metadata = it.metadata
                                        val jsonString = String(metadata)
                                        val jsonObject = JSONObject(jsonString)
                                        val name = jsonObject.get("name") ?: "unknown"
                                        it.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                        it.delete(object: OfflineRegion.OfflineRegionDeleteCallback {

                                            override fun onDelete() {
                                                Log.d(TAG,"$name deleted")
                                                progressBar.visibility = View.GONE
                                                cacheButton.isSelected = false
                                                cacheButton.text = getString(R.string.create_cache)
                                            }

                                            override fun onError(error: String?) {
                                                Log.d(TAG,"Could not delete $name. $error")
                                            }
                                        })
                                    }
                                }

                                override fun onError(error: String?) {
                                    Log.d(TAG,"Could not get status of offline pack $error")
                                }
                            })
                        }
                    }

                    override fun onError(error: String?) {
                        Log.d(TAG,"Could not load list of offline packs $error")
                    }
                })
            } else {
                cacheButton.isSelected = true
                cacheButton.text = getString(R.string.cancel)
                progressBar.visibility = View.VISIBLE
                startOfflinePackDownload()
            }
        }
    }

    /**
     * This method will cache current map style, current visible region and zoom levels
     * from current to max or if current is more than 13 the max is current + 2
     */
    private fun startOfflinePackDownload() {
        this.progressBar.progress = 0

        // Create a region that includes the current viewport and any tiles needed to view it when zoomed further in.
        // Because tile count grows exponentially with the maximum zoom level, you should be conservative with your `toZoomLevel` setting.

        mapboxMap?.let { map ->
            map.style?.let { style ->

                val visibleRegion = map.projection.visibleRegion.latLngBounds

                val region = OfflineTilePyramidRegionDefinition(
                        style.uri,
                        visibleRegion,
                        map.cameraPosition.zoom,
                        max(14.0, map.cameraPosition.zoom + 2.0),
                        applicationContext.resources.displayMetrics.density)

                offlinePacksCounter++
                val packName = "Offline Pack $offlinePacksCounter"
                val metadata: ByteArray?
                metadata = try {
                    val jsonObject = JSONObject()
                    jsonObject.put("name", packName)
                    jsonObject.put("minx", visibleRegion.lonWest)
                    jsonObject.put("miny", visibleRegion.latSouth)
                    jsonObject.put("maxx", visibleRegion.lonEast)
                    jsonObject.put("maxy", visibleRegion.latNorth)
                    val json = jsonObject.toString()
                    json.toByteArray()
                } catch (exception: Exception) {
                    Log.d(TAG,"Failed to encode metadata: ${exception.message}")
                    null
                }

                metadata?.let {
                    // Create and register an offline pack with the shared offline storage object.
                    offlineManager.createOfflineRegion(region, metadata, object: OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(offlineRegion: OfflineRegion?) {
                            offlineRegion?.setObserver(object: OfflineRegion.OfflineRegionObserver {

                                /**
                                 * Called when maximum allowed mapbox tiles is reached. For AccuTerra map style we are setting the max tiles count to unlimited.
                                 * For Mapbox styles, please read Mapbox documentation.
                                 */
                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                    Log.d(TAG,"Offline pack “$packName” reached limit of $limit tiles.")
                                    progressBar.visibility = View.GONE
                                    cacheButton.text = getString(R.string.create_cache)
                                }

                                /**
                                 * Is called when mapbox failes to cache a tile. Mapbox will try to download the tile again, so we don't need to cancel the download.
                                 */
                                override fun onError(error: OfflineRegionError?) {
                                    Log.d(TAG,"Offline pack “$packName” received error: $error")
                                }

                                /**
                                 * Called when Mapbox region status or progress changes
                                 */
                                override fun onStatusChanged(status: OfflineRegionStatus?) {
                                    status?.let {
                                        // Calculate the download percentage

                                        // Calculate the download percentage
                                        val progress = if (it.requiredResourceCount >= 0) {
                                            100 * it.completedResourceCount.toDouble() / it.requiredResourceCount.toDouble()
                                        } else {
                                            0.0
                                        }

                                        progressBar.progress = progress.toInt()

                                        if (it.isComplete) {
                                            Log.d(TAG,"Offline pack “$packName” completed: ${it.completedResourceSize}, ${it.completedResourceCount} resources")
                                            progressBar.visibility = View.GONE
                                            cacheButton.isSelected = false
                                            cacheButton.text = getString(R.string.create_cache)
                                        }
                                    }
                                }
                            })
                            // Start downloading.
                            offlineRegion?.setDownloadState(OfflineRegion.STATE_ACTIVE)
                        }

                        override fun onError(error: String?) {
                            Log.d(TAG,"Could not create offline region $error")
                            progressBar.visibility = View.GONE
                            cacheButton.text = getString(R.string.create_cache)
                        }
                    })
                }
            }
        }
    }
}