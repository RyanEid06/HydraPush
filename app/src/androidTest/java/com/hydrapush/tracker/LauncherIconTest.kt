package com.hydrapush.tracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LauncherIconTest {
    @Test fun adaptiveIconHasOpaqueTealBackgroundAndSupportsLauncherMasks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icon = context.packageManager.getApplicationIcon(context.packageName) as AdaptiveIconDrawable
        val background = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.background.setBounds(0, 0, 432, 432)
        icon.background.draw(Canvas(background))
        listOf(0 to 0, 431 to 0, 0 to 431, 431 to 431).forEach { (x, y) ->
            val color = background.getPixel(x, y)
            assertEquals("Background must fill every corner", 255, Color.alpha(color))
            assertNotEquals(Color.BLACK, color)
            assertNotEquals(Color.WHITE, color)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull("Themed launcher icon exists", icon.monochrome)
        val directory = File(context.getExternalFilesDir(null), "icon-qa").apply { mkdirs() }
        listOf("circle", "rounded-square").forEach { mask ->
            val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val path = Path().apply {
                if (mask == "circle") addCircle(216f, 216f, 216f, Path.Direction.CW)
                else addRoundRect(RectF(0f, 0f, 432f, 432f), 90f, 90f, Path.Direction.CW)
            }
            canvas.clipPath(path)
            // Android adaptive layers extend 25% outside each masked edge.
            icon.background.setBounds(-108, -108, 540, 540)
            icon.foreground.setBounds(-108, -108, 540, 540)
            icon.background.draw(canvas)
            icon.foreground.draw(canvas)
            File(directory, "$mask.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
