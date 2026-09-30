package com.chris.whisperloom.ui.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.nav.StepState

/** Beschreibung einer Assistenten-Seite (Illustration mit Bildtext, Texte, Chip, Aktionen, Inhalt). */
class StepUi(
    @param:DrawableRes val image: Int,
    @param:StringRes val imageText: Int,
    val title: String,
    val body: String,
    val state: StepState,
    val primary: StepAction,
    val secondary: StepAction? = null,
    val content: @Composable ColumnScope.() -> Unit = {},
)

class StepAction(
    val label: String,
    val enabled: Boolean = true,
    val working: Boolean = false,
    @param:DrawableRes val trailingIcon: Int? = null,
    val onClick: () -> Unit,
)

/** Was ein Schritt vom Rahmen braucht: weiter zum naechsten Schritt, Snackbar. */
class StepActions(val snack: SnackController, val next: () -> Unit)
