package pt.isec.amov.cityfix.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
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
import org.osmdroid.views.overlay.Polygon
import pt.isec.amov.cityfix.model.BusinessRules
import pt.isec.amov.cityfix.model.Incident

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

/**
 * Mapa interativo para o Dashboard:
 *  - Anónimo: mostra círculos laranja clarinho semitransparentes (área geral, sem localização exata);
 *  - Autenticado: mostra pins exatos clicáveis que abrem o detalhe da ocorrência.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun DashboardIncidentsMap(
    incidents: List<Incident>,
    isAnonymous: Boolean,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnClick by rememberUpdatedState(onIncidentClick)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(if (isAnonymous) 12.8 else 13.8)
            controller.setCenter(GeoPoint(BusinessRules.PRACA_REPUBLICA_LAT, BusinessRules.PRACA_REPUBLICA_LON))
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }

    DisposableEffect(mapView) {
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
            view.overlays.clear()
            incidents.filter { it.latitude != 0.0 || it.longitude != 0.0 }.forEach { incident ->
                val point = GeoPoint(incident.latitude, incident.longitude)
                if (isAnonymous) {
                    val circle = Polygon(view).apply {
                        points = Polygon.pointsAsCircle(point, 350.0)
                        fillPaint.color = AndroidColor.argb(75, 255, 167, 38) // Laranja clarinho semitransparente
                        outlinePaint.color = AndroidColor.argb(180, 245, 124, 0)
                        outlinePaint.strokeWidth = 3f
                        title = "${incident.categoryName} (${incidentStatusLabel(incident.status)})"
                    }
                    view.overlays.add(circle)
                } else {
                    val marker = Marker(view).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = incident.title
                        snippet = incidentStatusLabel(incident.status)
                        setOnMarkerClickListener { _, _ ->
                            currentOnClick(incident.id)
                            true
                        }
                    }
                    view.overlays.add(marker)
                }
            }
            view.invalidate()
        }
    )
}