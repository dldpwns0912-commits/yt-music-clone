package com.ytmusic.feature.equalizer

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ytmusic.core.designsystem.theme.YtBlack
import com.ytmusic.core.designsystem.theme.YtChipBackground
import com.ytmusic.core.designsystem.theme.YtRedBright
import com.ytmusic.core.designsystem.theme.YtSurface
import com.ytmusic.core.designsystem.theme.YtSurfaceVariant
import com.ytmusic.core.designsystem.theme.YtTextPrimary
import com.ytmusic.core.designsystem.theme.YtTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onNavigateBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val isEqEnabled by viewModel.isEqualizerEnabled.collectAsState()
    val isNormEnabled by viewModel.isNormalizationEnabled.collectAsState()
    val bassBoost by viewModel.bassBoostStrength.collectAsState()
    val bands by viewModel.bands.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YtBlack)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Equalizer & Effects",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = YtBlack)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Volume Normalization Card (-14 LUFS)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = YtSurfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Volume Normalization",
                                color = YtTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Standardizes volume to -14 LUFS across all tracks using LoudnessEnhancer to prevent ear shock.",
                                color = YtTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isNormEnabled,
                            onCheckedChange = { viewModel.setNormalizationEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = YtRedBright
                            )
                        )
                    }
                }
            }

            // 2. Master Equalizer Toggle & Presets Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = YtSurfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Equalizer",
                                color = YtTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = isEqEnabled,
                                onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = YtRedBright
                                )
                            )
                        }

                        if (isEqEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Presets", color = YtTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(EqPreset.entries.toTypedArray()) { preset ->
                                    val isSelected = selectedPreset == preset
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) Color.White else YtChipBackground)
                                            .clickable { viewModel.applyPreset(preset) }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = preset.label,
                                            color = if (isSelected) YtBlack else YtTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 5 Frequency Bands
                            bands.forEach { band ->
                                val freqText = if (band.centerFreqHz >= 1000) {
                                    "${band.centerFreqHz / 1000} kHz"
                                } else {
                                    "${band.centerFreqHz} Hz"
                                }
                                val dbVal = band.currentLevelMb / 100

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = freqText,
                                        color = YtTextSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(60.dp)
                                    )
                                    Slider(
                                        value = band.currentLevelMb.toFloat(),
                                        onValueChange = { newVal ->
                                            viewModel.setBandLevel(band.bandIndex, newVal.toInt().toShort())
                                        },
                                        valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = YtRedBright,
                                            inactiveTrackColor = Color(0x33FFFFFF)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${if (dbVal > 0) "+$dbVal" else "$dbVal"} dB",
                                        color = YtTextPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(48.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Bass Boost Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = YtSurfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bass Boost",
                                color = YtTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${bassBoost / 10}%",
                                color = YtRedBright,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = bassBoost.toFloat(),
                            onValueChange = { viewModel.setBassBoost(it.toInt()) },
                            valueRange = 0f..1000f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = YtRedBright,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
