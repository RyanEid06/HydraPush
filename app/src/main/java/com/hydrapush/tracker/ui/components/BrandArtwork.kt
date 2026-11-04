package com.hydrapush.tracker.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hydrapush.tracker.R

object BrandArtwork {
    fun runner(resources: Resources): Bitmap {
        val image = BitmapFactory.decodeResource(resources, R.drawable.header_runner)
        // The supplied decorative crop includes a stray line at its top edge.
        val top = (image.height * 0.025f).toInt()
        return Bitmap.createBitmap(image, 0, top, image.width, image.height - top)
    }
}
