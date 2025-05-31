package com.vi5hnu.nightshield.widgets

import android.util.Range
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.skydoves.colorpicker.compose.*

@Composable
fun ColorPicker(
    predefinedColors: List<Color> = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta
    ),
    sliderRange: ClosedFloatingPointRange<Float> = 0f..1f,
    initialColor: Color = Color.Red,
    onChange: (Color) -> Unit,
) {
    val controller = rememberColorPickerController()
    var selectedColor by remember { mutableStateOf(initialColor) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }
    val scrollState= rememberScrollState();

    Box{
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Black.copy(alpha=0.6f), shape = RoundedCornerShape(12.dp))
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color Wheel
            HsvColorPicker(
                modifier = Modifier
                    .size(250.dp)
                    .padding(8.dp),
                controller = controller,
                onColorChanged = { colorEnvelope ->
                    if(colorEnvelope.fromUser) onChange(colorEnvelope.color.copy(alpha = alpha))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Alpha Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Alpha", fontSize = 14.sp)
                Slider(
                    value = alpha,
                    onValueChange = {
                        alpha = it
                        onChange(selectedColor.copy(alpha = alpha))
                    },
                    valueRange = sliderRange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Predefined Colors
            LazyRow {
                items(predefinedColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color)
                            .border(
                                2.dp, if (color == selectedColor) Color.Black else Color.Transparent, CircleShape
                            )
                            .clickable { onChange(color.copy(alpha = alpha)) }
                    )
                }
            }
        }
    }
}
