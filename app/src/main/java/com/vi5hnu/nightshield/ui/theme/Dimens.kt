package com.vi5hnu.nightshield.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Layout tokens for the whole app.
 *
 * Every padding, gap, corner radius and icon box in the UI comes from here instead of being typed
 * inline, so the visual rhythm stays consistent and a spacing change is a one-line edit. All values
 * are multiples of 4dp, matching the Material grid.
 */
object Spacing {
    /** Hairline gaps inside a single control (icon ↔ its label). */
    val xs = 4.dp
    /** Related elements inside one row or chip. */
    val sm = 8.dp
    /** Default gap between stacked controls. */
    val md = 12.dp
    /** Screen gutter and card inner padding. */
    val lg = 16.dp
    /** Gap between a section header and its content. */
    val xl = 20.dp
    /** Gap between two sections. */
    val xxl = 24.dp
    /** Breathing room above a screen's first section / below its last. */
    val xxxl = 32.dp
}

/** Corner radii. Larger surfaces get larger radii so nesting reads correctly. */
object Radius {
    /** Chips, badges, small inline surfaces. */
    val sm = 12.dp
    /** Buttons, banners, dialogs' inner surfaces. */
    val md = 16.dp
    /** Cards and list groups. */
    val lg = 20.dp
    /** Hero / feature surfaces. */
    val xl = 28.dp
}

/** Icon box sizes. Compose scales the 24dp vectors to these. */
object IconSize {
    /** Inline with label text (badges, chips). */
    val sm = 16.dp
    /** Standard leading icon in a settings row. */
    val md = 24.dp
    /** Emphasised icon inside a tinted container. */
    val lg = 28.dp
    /** Hero / empty-state illustration. */
    val xl = 44.dp
}
