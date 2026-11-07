package com.hydrapush.tracker

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertFalse

internal fun assertNoClippedText(rule: ComposeContentTestRule) {
    val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
    rule.runOnIdle {
        val failures = mutableListOf<String>()
        nodes.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { result ->
                val input = result.layoutInput
                // Compose 1.7's semantics paragraph retains the available width; measure against the actual rendered bounds.
                val measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0).measure(
                    text = input.text, style = input.style, overflow = input.overflow, softWrap = input.softWrap,
                    maxLines = input.maxLines, placeholders = input.placeholders,
                    constraints = Constraints(maxWidth = result.size.width, maxHeight = result.size.height)
                )
                if (measured.hasVisualOverflow) failures += "${input.text}: rendered=${result.size}, text=${measured.size}"
            }
        }
        assertFalse("Text must fit its rendered bounds: ${failures.joinToString("; ")}", failures.isNotEmpty())
    }
}
