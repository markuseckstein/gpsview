package de.eckstein.gpsview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.eckstein.gpsview.coordinates.PositionSnapshot
import kotlinx.coroutines.launch
import kotlin.time.TimeSource
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.PositionWithAccuracy
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.extensions.meters

/** OpenFreeMap's neutral Liberty style (research.md R2/E6). */
private const val BASE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val FIRST_FIX_ZOOM = 16.0

/** Bavarian attribution line — one line covers both DOP20 and ALKIS (contracts/map-services.md). */
private const val BAVARIA_ATTRIBUTION = "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"

/** DOP20 orthophoto WMS `GetMap` template, color variant (contracts/map-services.md §2). */
private const val DOP20_TILE_URL =
    "https://geoservices.bayern.de/od/wms/dop/v1/dop20" +
        "?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap" +
        "&LAYERS=by_dop20c&STYLES=" +
        "&CRS=EPSG:3857&BBOX={bbox-epsg-3857}" +
        "&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=FALSE"

/** ALKIS-Parzellarkarte WMS `GetMap` template — `STYLES=Gelb` is mandatory (contracts/map-services.md §3). */
private const val ALKIS_TILE_URL =
    "https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte" +
        "?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap" +
        "&LAYERS=by_alkis_parzellarkarte_umr_gelb&STYLES=Gelb" +
        "&CRS=EPSG:3857&BBOX={bbox-epsg-3857}" +
        "&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE"

/** No parcel lines before individual parcels are legible (contracts/map-services.md §3). */
private const val ALKIS_MIN_ZOOM = 16f

/**
 * Full-bleed MapLibre map (SPEC.md §8): OpenFreeMap base, own-position blue-dot + accuracy circle,
 * follow-me camera, floating locate/zoom tools, rotation+tilt locked north-up, and the two
 * Bavarian overlay layers (DOP20/ALKIS), each gated on [mapUiState].
 */
@Composable
fun MapContent(
    positionState: PositionUiState,
    mapUiState: MapUiState,
    onFollowMeChanged: (Boolean) -> Unit,
    onToggleSatelliteLayer: () -> Unit,
    onToggleParcelsLayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraState = rememberCameraState()
    val snapshot = (positionState as? PositionUiState.Live)?.snapshot
    var hasCenteredOnFirstFix by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(snapshot) {
        val current = snapshot ?: return@LaunchedEffect
        val target = Position(current.latLon.longitude, current.latLon.latitude)
        if (!hasCenteredOnFirstFix) {
            hasCenteredOnFirstFix = true
            cameraState.animateTo(CameraPosition(target = target, zoom = FIRST_FIX_ZOOM))
        } else if (mapUiState.followMe) {
            cameraState.animateTo(CameraPosition(target = target, zoom = cameraState.position.zoom))
        }
    }

    // A gesture-initiated camera move (drag) disengages follow-me (SPEC.md §8.2).
    LaunchedEffect(cameraState.moveReason) {
        if (cameraState.moveReason == CameraMoveReason.GESTURE && mapUiState.followMe) {
            onFollowMeChanged(false)
        }
    }

    Box(modifier = modifier) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri(BASE_STYLE_URL),
            cameraState = cameraState,
            options =
                MapOptions(
                    gestureOptions = GestureOptions.RotationLocked,
                    ornamentOptions = OrnamentOptions(isCompassEnabled = false),
                ),
        ) {
            val dop20Source =
                rememberRasterSource(
                    tiles = listOf(DOP20_TILE_URL),
                    options = TileSetOptions(attributionHtml = BAVARIA_ATTRIBUTION),
                    tileSize = 256,
                )
            RasterLayer(id = "dop20", source = dop20Source, visible = mapUiState.satelliteVisible)

            val alkisSource =
                rememberRasterSource(
                    tiles = listOf(ALKIS_TILE_URL),
                    options = TileSetOptions(attributionHtml = BAVARIA_ATTRIBUTION),
                    tileSize = 256,
                )
            RasterLayer(
                id = "alkis-parzellarkarte",
                source = alkisSource,
                minZoom = ALKIS_MIN_ZOOM,
                visible = mapUiState.parcelsVisible,
            )

            LocationPuck(
                idPrefix = "own-position",
                location = snapshot?.toMaplibreLocation(),
                cameraState = cameraState,
                showBearing = false,
                showBearingAccuracy = false,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mapUiState.satelliteVisible,
                onClick = onToggleSatelliteLayer,
                label = { Text("Satellit") },
                leadingIcon = { Icon(Icons.Default.Satellite, contentDescription = null) },
            )
            FilterChip(
                selected = mapUiState.parcelsVisible,
                onClick = onToggleParcelsLayer,
                label = { Text("Flurstücke") },
                leadingIcon = { Icon(Icons.Default.Terrain, contentDescription = null) },
            )
        }

        // The built-in attribution control's dialog does not surface a WMS raster source's
        // TileSetOptions.attributionHtml on Android (verified on-device, T041) — templated
        // GetMap URLs aren't real TileJSON sources with an attribution manifest the control
        // reads. Shown explicitly here instead so the CC BY 4.0 obligation is met reliably
        // (contracts/map-services.md attribution rules) whenever either Bavarian layer is on.
        if (mapUiState.satelliteVisible || mapUiState.parcelsVisible) {
            Text(
                text = BAVARIA_ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 56.dp, bottom = 4.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        cameraState.animateTo(cameraState.position.copy(zoom = cameraState.position.zoom + 1))
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Vergrößern")
            }
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        cameraState.animateTo(cameraState.position.copy(zoom = cameraState.position.zoom - 1))
                    }
                }
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Verkleinern")
            }
            FloatingActionButton(
                onClick = {
                    onFollowMeChanged(true)
                    val current = snapshot ?: return@FloatingActionButton
                    coroutineScope.launch {
                        cameraState.animateTo(
                            CameraPosition(
                                target = Position(current.latLon.longitude, current.latLon.latitude),
                                zoom = cameraState.position.zoom,
                            )
                        )
                    }
                }
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Eigenen Standort zentrieren")
            }
        }
    }
}

private fun PositionSnapshot.toMaplibreLocation(): Location =
    Location(
        position =
            PositionWithAccuracy(
                value = Position(latLon.longitude, latLon.latitude),
                accuracy = horizontalAccuracyM?.toDouble()?.meters,
            ),
        timestamp = TimeSource.Monotonic.markNow(),
    )
