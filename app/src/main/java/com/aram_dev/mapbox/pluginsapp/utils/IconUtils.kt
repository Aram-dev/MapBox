package com.aram_dev.mapbox.pluginsapp.utils

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.ColorInt
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.drawable.DrawableCompat
import com.mapbox.mapboxsdk.annotations.Icon
import com.mapbox.mapboxsdk.annotations.IconFactory

    /**
     * Demonstrates converting any Drawable to an Icon, for use as a marker icon.
     */
    fun drawableToIcon(context: Context, @DrawableRes id: Int, @ColorInt colorRes: Int): Icon {
        val vectorDrawable = ResourcesCompat.getDrawable(context.resources, id, context.theme)
        val bitmap = Bitmap.createBitmap(
            vectorDrawable!!.intrinsicWidth,
            vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        DrawableCompat.setTint(vectorDrawable, colorRes)
        vectorDrawable.draw(canvas)
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }