package com.vi5hnu.nightshield.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.*
import com.vi5hnu.nightshield.R

@Composable
fun ColorPicker(
    predefinedColors: List<Color> = listOf(
        Color(0xCCFF6000), Color(0xBBFF8C00), Color(0x99FFA500),
        Color(0xCC8B0000), Color(0xEE130030), Color(0x663ABDE0),
    ),
    sliderRange: ClosedFloatingPointRange<Float> = 0.05f..0.9f,
    initialColor: Color = Color(0x80FFA500),
    onChange: (Color) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    val controller = rememberColorPickerController()
    var selectedColor by remember { mutableStateOf(initialColor) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha.coerceIn(0.05f, 0.9f)) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Choose Filter Color",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(20.dp))

        // HSV color wheel
        HsvColorPicker(
            modifier = Modifier
                .size(260.dp)
                .padding(8.dp),
            controller = controller,
            onColorChanged = { envelope ->
                if (envelope.fromUser) {
                    selectedColor = envelope.color
                    onChange(envelope.color.copy(alpha = alpha))
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // Alpha / opacity slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.alpha_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp)
            )
            Slider(
                value = alpha,
                onValueChange = {
                    alpha = it
                    onChange(selectedColor.copy(alpha = alpha))
                },
                valueRange = sliderRange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            // Live preview swatch
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(selectedColor.copy(alpha = alpha))
                    .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Quick-select predefined colors
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(predefinedColors) { color ->
                val isSelected = color == selectedColor
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 1f))
                        .border(
                            width = if (isSelected) 3.dp else 1.5.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable {
                            selectedColor = color
                            onChange(color.copy(alpha = alpha))
                        }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Done button — color is already live-applied via onChange during drag; just commit (close)
        Button(
            onClick = { onDismiss?.invoke() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Apply Color", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(8.dp))
    }
}
