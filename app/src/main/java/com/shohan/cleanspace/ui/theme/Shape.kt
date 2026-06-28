package com.shohan.cleanspace.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// More restrained corner radii than before (was 8–36dp, quite bubbly/playful)
// — a tighter range reads as more "classic professional" and less like a
// casual consumer app.
val CleanSpaceShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
