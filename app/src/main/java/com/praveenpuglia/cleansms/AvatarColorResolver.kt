package com.praveenpuglia.cleansms

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
import kotlin.math.abs

object AvatarColorResolver {
    private data class PaletteEntry(
        val backgroundAttr: Int,
        val backgroundFallbackRes: Int,
        val foregroundAttr: Int,
        val foregroundFallbackRes: Int
    )

    private val palette = listOf(
        PaletteEntry(
            com.google.android.material.R.attr.colorPrimaryContainer,
            R.color.avatar_fallback_bg_primary,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            R.color.avatar_fallback_fg_primary
        ),
        PaletteEntry(
            com.google.android.material.R.attr.colorSecondaryContainer,
            R.color.avatar_fallback_bg_secondary,
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            R.color.avatar_fallback_fg_secondary
        ),
        PaletteEntry(
            com.google.android.material.R.attr.colorTertiaryContainer,
            R.color.avatar_fallback_bg_tertiary,
            com.google.android.material.R.attr.colorOnTertiaryContainer,
            R.color.avatar_fallback_fg_tertiary
        ),
        PaletteEntry(
            com.google.android.material.R.attr.colorSurfaceVariant,
            R.color.avatar_fallback_bg_surface_variant,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            R.color.avatar_fallback_fg_surface_variant
        ),
        PaletteEntry(
            com.google.android.material.R.attr.colorErrorContainer,
            R.color.avatar_fallback_bg_error_container,
            com.google.android.material.R.attr.colorOnErrorContainer,
            R.color.avatar_fallback_fg_error_container
        )
    )

    fun applyTo(textView: TextView, key: String) {
        val (background, foreground) = resolve(textView.context, key.ifBlank { "?" })
        val backgroundDrawable = textView.background
        if (backgroundDrawable is GradientDrawable) {
            backgroundDrawable.mutate()
            backgroundDrawable.setColor(background)
        } else {
            ViewCompat.setBackgroundTintList(textView, ColorStateList.valueOf(background))
        }
        textView.setTextColor(foreground)
    }

    private fun resolve(context: Context, key: String): Pair<Int, Int> {
        if (palette.isEmpty()) {
            val defaultBg = ContextCompat.getColor(context, R.color.avatar_fallback_bg_primary)
            val defaultFg = ContextCompat.getColor(context, R.color.avatar_fallback_fg_primary)
            return defaultBg to defaultFg
        }
        val resolved = palette.map { entry ->
            val background = MaterialColors.getColor(
                context,
                entry.backgroundAttr,
                ContextCompat.getColor(context, entry.backgroundFallbackRes)
            )
            val foreground = MaterialColors.getColor(
                context,
                entry.foregroundAttr,
                ContextCompat.getColor(context, entry.foregroundFallbackRes)
            )
            background to foreground
        }
        val index = abs(key.hashCode()) % resolved.size
        return resolved[index]
    }
}
