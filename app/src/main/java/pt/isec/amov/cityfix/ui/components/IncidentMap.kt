package pt.isec.amov.cityfix.ui.components

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Mapa OpenStreetMap (osmdroid, alternativa open-source de P10) com o pin da ocorrência.
 * Se [onLocationPicked] for indicado, um toque no mapa escolhe uma nova localização.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun IncidentMap(
    latitude: Double,
    longitude: Double,
    zoom: Double,
    modifier: Modifier = Modifier,
    markerTitle: String? = null,
    onLocationPicked: ((Double, Double) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onLocationPicked)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            // Dentro de ecrãs com scroll, o mapa fica com os gestos de arrastar
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val marker = remember { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }

    DisposableEffect(mapView) {
        mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                val picker = currentOnPicked ?: return false
                picker(p.latitude, p.longitude)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
        mapView.overlays.add(marker)
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            val point = GeoPoint(latitude, longitude)
            marker.position = point
            marker.title = markerTitle
            view.controller.setZoom(zoom)
            view.controller.setCenter(point)
            view.invalidate()
        }
    )
}
