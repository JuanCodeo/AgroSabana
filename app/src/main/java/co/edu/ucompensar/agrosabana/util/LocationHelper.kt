package co.edu.ucompensar.agrosabana.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import co.edu.ucompensar.agrosabana.data.GeoPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Captura de la ubicacion del usuario con Google Play Services (FusedLocationProviderClient) y
 * conversion de coordenadas a direccion con Geocoder (geocodificacion inversa de Google).
 *
 * Se debe crear en la propiedad de la Activity (antes de onStart) porque registra el lanzador del
 * permiso de ubicacion en tiempo de ejecucion.
 */
class LocationHelper(
    private val activity: ComponentActivity,
    private val onLocation: (GeoPoint) -> Unit,
    private val onDenied: () -> Unit
) {

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) fetch() else onDenied()
    }

    /** Pide el permiso si hace falta y luego obtiene la posicion actual. */
    fun request() {
        if (hasPermission(activity)) fetch()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @SuppressLint("MissingPermission")
    private fun fetch() {
        val client = LocationServices.getFusedLocationProviderClient(activity)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocation(GeoPoint(location.latitude, location.longitude))
                } else {
                    // Sin posicion fresca (GPS apagado): se intenta con la ultima conocida.
                    client.lastLocation.addOnSuccessListener { last ->
                        if (last != null) onLocation(GeoPoint(last.latitude, last.longitude)) else onDenied()
                    }.addOnFailureListener { onDenied() }
                }
            }
            .addOnFailureListener { onDenied() }
    }

    companion object {
        fun hasPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Obtiene la posicion sin pedir permiso (solo si ya fue concedido); null si no se puede. */
        @SuppressLint("MissingPermission")
        suspend fun lastKnown(context: Context): GeoPoint? {
            if (!hasPermission(context)) return null
            return runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
                    ?.let { GeoPoint(it.latitude, it.longitude) }
            }.getOrNull()
        }

        /**
         * Convierte coordenadas en una direccion legible, por ejemplo "Cra. 15 #8-21, Funza".
         * Devuelve null si no hay conexion o el servicio no encuentra resultados.
         */
        suspend fun reverseGeocode(context: Context, point: GeoPoint): String? {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale("es", "CO"))
            val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(point.lat, point.lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(point.lat, point.lng, 1)?.firstOrNull() }.getOrNull()
                }
            }
            return address?.let(::format)
        }

        private fun format(address: Address): String {
            val street = listOfNotNull(address.thoroughfare, address.subThoroughfare).joinToString(" # ")
            val city = address.locality ?: address.subAdminArea
            val parts = listOf(street, city).filter { !it.isNullOrBlank() }
            return if (parts.isNotEmpty()) parts.joinToString(", ") else address.getAddressLine(0).orEmpty()
        }
    }
}
