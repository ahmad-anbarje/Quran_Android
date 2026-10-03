package com.readqurantoday.quran

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/** The app's own face, for text drawn on a canvas where the theme's fontFamily cannot reach. */
fun uiFont(context: Context): Typeface? = ResourcesCompat.getFont(context, R.font.cairo)
