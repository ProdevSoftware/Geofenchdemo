package com.example.geofenchdemo

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var geofencingClient: GeofencingClient
    private val geofenceList = mutableListOf<Geofence>()

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        //check permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            currentLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                REQUEST_CODE
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun currentLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                mapFragment.getMapAsync { googlemap ->
                    val latlng = LatLng(location.latitude, location.longitude)
                    googlemap.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 10f))
                    googlemap.addMarker(MarkerOptions().position(latlng).title("You are Here"))
                }
            }
        }
    }

    private fun createLocationRequest() {
        val locationRequest = LocationRequest.create().apply {
            interval = LOCATION_INTERVAL
            fastestInterval = LOCATION_FASTEST_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)


        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            Log.i("Location", " Enable Successful")

        }
        task.addOnFailureListener { exception ->
            Log.i("Location", exception.message.toString())
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(
                        this,
                        REQUEST_CHECK_SETTINGS
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        createLocationRequest()
        checkPermissionsAndCreateGeofence(googleMap, this)
    }

    private fun checkPermissionsAndCreateGeofence(googleMap: GoogleMap, context: Context) {
        // Check if permission to access fine location and background location is granted
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions are granted, create geofence
            createGeofence(googleMap)
        } else {
            Toast.makeText(this, "Permissions are not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createGeofence(googleMap: GoogleMap) {
        //Write here for the geo fence
        googleMap.setOnMapClickListener { latLng ->
            geofenceList.add(
                Geofence.Builder()
                    .setRequestId("entry.key")
                    .setCircularRegion(latLng.latitude, latLng.longitude, RADIUS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            )
            drawCircleOnMap(latLng)
            addGeofenceRequest()
        }
    }

    private fun drawCircleOnMap(latLng: LatLng) {

        val circleOptions = CircleOptions()
            .center(latLng)
            .radius(RADIUS.toDouble())
            .fillColor(0x40ff0000)
            .strokeColor(Color.BLUE)
            .strokeWidth(2f)

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10f))
        mMap.addMarker(MarkerOptions().position(latLng))
        mMap.addCircle(circleOptions)
    }

    @SuppressLint("MissingPermission")
    private fun addGeofenceRequest() {
        geofencingClient.addGeofences(getGeofenceRequest(), geofencePendingIntent).run {
            addOnSuccessListener {
                Toast.makeText(
                    this@MapsActivity,
                    "Geofence is added successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            addOnFailureListener {
                Log.e("Error", it.localizedMessage)
                Toast.makeText(this@MapsActivity, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getGeofenceRequest(): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (grantResults.isNotEmpty() && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                currentLocation()
            }
        } else {
            Toast.makeText(this, "Please enable the location", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                Log.i("Request Code", requestCode.toString())
                // Here we need to called Client Request Update.
            } else {
                Log.i("Request Code", "Please enable the location")
                Toast.makeText(this, "Please enable the location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeGeofence()
    }

    private fun removeGeofence() {
        geofencingClient.removeGeofences(geofencePendingIntent).run {
            addOnSuccessListener {
                Toast.makeText(
                    this@MapsActivity,
                    "Geofence is removed successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            addOnFailureListener {
                Toast.makeText(this@MapsActivity, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val RADIUS = 10000f
        const val REQUEST_CODE = 101
        const val REQUEST_CHECK_SETTINGS = 101
        const val LOCATION_INTERVAL = 10000L
        const val LOCATION_FASTEST_INTERVAL = 5000L
    }
}