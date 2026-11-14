package com.kunvarpreet.ascend.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/**
 * Clean abstraction over device location telemetry.
 * Wraps Android platform LocationManager with GPS and Network provider fallbacks,
 * decoupled from proprietary Google Play Services for reliable offline operation.
 */
interface LocationProviderClient {
    fun startLocationUpdates(onLocation: (Location) -> Unit)
    fun stopLocationUpdates()
}

class PlatformLocationProviderClient(
    private val context: Context
) : LocationProviderClient {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var activeListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates(onLocation: (Location) -> Unit) {
        stopLocationUpdates()
        val mgr = locationManager ?: return

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        activeListener = listener

        try {
            // Register GPS provider
            if (mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mgr.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            // Register Network provider fallback
            if (mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mgr.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2.0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
            // Permissions missing or revoked
        }
    }

    override fun stopLocationUpdates() {
        activeListener?.let {
            try {
                locationManager?.removeUpdates(it)
            } catch (_: Exception) {}
        }
        activeListener = null
    }
}
