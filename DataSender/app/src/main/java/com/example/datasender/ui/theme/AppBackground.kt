package com.example.datasender.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val SpeedBgBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF050A1A), // góra – deep navy
        Color(0xFF081436), // środek – niebieski pod logo
        Color(0xFF060B1E)  // dół – ciemny granat
    )
)
