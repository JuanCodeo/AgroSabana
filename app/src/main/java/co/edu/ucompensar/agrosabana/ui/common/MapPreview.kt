package co.edu.ucompensar.agrosabana.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import co.edu.ucompensar.agrosabana.BuildConfig
import co.edu.ucompensar.agrosabana.data.GeoPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Vista previa de un punto en Google Maps (Maps SDK for Android, modo lite).
 *
 * El modo lite dibuja un mapa estatico liviano, ideal para mostrar la direccion de entrega dentro
 * de un formulario con scroll. Mientras no hay coordenadas (o si falta la llave de Maps) se muestra
 * la ilustracion del layout. Los hijos declarados en el XML (pin, boton) se mantienen encima.
 */
class MapPreview @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var mapView: MapView? = null

    /** true si la app tiene llave de Maps configurada en local.properties. */
    private val mapsEnabled: Boolean = BuildConfig.MAPS_ENABLED

    /** Centra el mapa en [point] con un marcador; null vuelve a la ilustracion. */
    fun show(point: GeoPoint?, title: String? = null) {
        if (point == null || !mapsEnabled) {
            mapView?.visibility = View.GONE
            setPlaceholderVisible(true)
            return
        }
        val view = mapView ?: createMap()
        view.visibility = View.VISIBLE
        setPlaceholderVisible(false)
        view.getMapAsync { map ->
            val position = LatLng(point.lat, point.lng)
            map.clear()
            map.uiSettings.isMapToolbarEnabled = false
            map.addMarker(MarkerOptions().position(position).title(title))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, ZOOM))
            // Al tocar el mapa se abre Google Maps en ese punto.
            map.setOnMapClickListener { openInMaps(context, point, title) }
        }
    }

    private fun createMap(): MapView {
        MapsInitializer.initialize(context)
        val view = MapView(context, GoogleMapOptions().liteMode(true))
        view.onCreate(null)
        view.onResume()
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mapView = view
        return view
    }

    /** Oculta el pin ilustrativo (los hijos sin id); los botones con id siguen visibles. */
    private fun setPlaceholderVisible(visible: Boolean) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== mapView && child.id == View.NO_ID) child.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val ZOOM = 16f

        /** Abre la app de Google Maps (o cualquier app de mapas) con un marcador en el punto. */
        fun openInMaps(context: Context, point: GeoPoint, label: String?) {
            val query = Uri.encode("${point.lat},${point.lng}(${label ?: "Entrega"})")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${point.lat},${point.lng}?q=$query"))
            runCatching { context.startActivity(intent) }
        }
    }
}
