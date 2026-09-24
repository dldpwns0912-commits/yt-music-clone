package com.ytmusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.designsystem.theme.YtBlack
import com.ytmusic.core.designsystem.theme.YtCardSurface
import com.ytmusic.core.designsystem.theme.YtChipBackground
import com.ytmusic.core.designsystem.theme.YtRed
import com.ytmusic.core.designsystem.theme.YtSurfaceVariant
import com.ytmusic.core.designsystem.theme.YtTextPrimary
import com.ytmusic.core.designsystem.theme.YtTextSecondary
import com.ytmusic.core.extractor.model.TrackMetadata

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onTrackClick: (TrackMetadata, List<TrackMetadata>) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickPicks by homeViewModel.quickPicks.collectAsState()
    val recentlyPlayed by homeViewModel.recentlyPlayed.collectAsState()
    val mostPlayed by homeViewModel.mostPlayed.collectAsState()

    val moodChips = listOf("Energize", "Relax", "Workout", "Focus", "Commute")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(YtBlack),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // 1. Header with YouTube Music Branding & Actions
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(YtRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Logo",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Music",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "Alerts", tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = Color.White)
                    }
                }
            }
        }

        // 2. Mood & Activity Filter Chips
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(moodChips) { mood ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(YtChipBackground)
                            .clickable {}
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = mood,
                            color = YtTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Quick Picks Section
        item {
            SectionTitle(title = "START RADIO FROM A SONG", subtitle = "Quick picks")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quickPicks.forEach { track ->
                    QuickPickItem(
                        track = track,
                        onClick = { onTrackClick(track, quickPicks) }
                    )
                }
            }
        }

        // 4. Recently Played Section
        if (recentlyPlayed.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle(title = "LISTEN AGAIN", subtitle = "Recently played")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyPlayed) { entity ->
                        val metadata = entity.toTrackMetadata()
                        HorizontalTrackCard(
                            track = metadata,
                            onClick = { onTrackClick(metadata, recentlyPlayed.map { it.toTrackMetadata() }) }
                        )
                    }
                }
            }
        }

        // 5. Most Played Section
        if (mostPlayed.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle(title = "YOUR FAVORITES", subtitle = "Most played")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mostPlayed) { entity ->
                        val metadata = entity.toTrackMetadata()
                        HorizontalTrackCard(
                            track = metadata,
                            onClick = { onTrackClick(metadata, mostPlayed.map { it.toTrackMetadata() }) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = YtTextSecondary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleLarge.copy(
                color = YtTextPrimary,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun QuickPickItem(
    track: TrackMetadata,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(YtCardSurface)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = YtTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album ?: "Single"}",
                color = YtTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HorizontalTrackCard(
    track: TrackMetadata,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(YtCardSurface)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            color = YtTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            color = YtTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun TrackEntity.toTrackMetadata(): TrackMetadata {
    return TrackMetadata(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        thumbnailUrl = thumbnailUrl
    )
}
