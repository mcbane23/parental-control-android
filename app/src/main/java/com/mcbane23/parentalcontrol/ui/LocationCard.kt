package com.mcbane23.parentalcontrol.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mcbane23.parentalcontrol.data.ChildLocation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** The child's last known position on an OpenStreetMap map, with its age and accuracy. */
@Composable
fun LocationCard(
    childName: String,
    location: ChildLocation?,
    canManage: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(Icons.Filled.LocationOn, "Location")
            if (location == null) {
                Text(
                    "No position yet. It appears once location is allowed on the child's phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.small),
                    factory = { ctx ->
                        // OpenStreetMap asks every app to identify itself when loading map tiles.
                        Configuration.getInstance().userAgentValue = ctx.packageName
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(16.0)
                        }
                    },
                    update = { map ->
                        val point = GeoPoint(location.latitude, location.longitude)
                        map.overlays.clear()
                        map.overlays.add(
                            Marker(map).apply {
                                position = point
                                title = childName
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            },
                        )
                        map.controller.setCenter(point)
                        map.invalidate()
                    },
                    onRelease = { it.onDetach() },
                )
                val ago = DateUtils.getRelativeTimeSpanString(
                    location.timeMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                )
                val details = buildList {
                    add("Updated $ago")
                    location.accuracyMeters?.let { add("accurate to about ${it.toInt()} m") }
                    location.batteryPercent?.let { add("battery $it%") }
                }
                Text(
                    details.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canManage) {
                    FilledTonalButton(onClick = onRefresh, enabled = !busy, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh")
                    }
                }
                if (location != null) {
                    OutlinedButton(
                        shape = MaterialTheme.shapes.small,
                        onClick = {
                            val uri = Uri.parse(
                                "geo:${location.latitude},${location.longitude}" +
                                    "?q=${location.latitude},${location.longitude}(${Uri.encode(childName)})",
                            )
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    ) { Text("Open in Maps") }
                }
            }
        }
    }
}
