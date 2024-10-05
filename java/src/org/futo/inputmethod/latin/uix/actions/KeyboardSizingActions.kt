package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionBarHeight
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.v2keyboard.KeyboardMode
import org.futo.inputmethod.v2keyboard.KeyboardSizingCalculator
import org.futo.voiceinput.shared.ui.theme.Typography

@Composable
private fun RowScope.KeyboardMode(iconRes: Int, checkedIconRes: Int, name: String, sizingCalculator: KeyboardSizingCalculator, mode: KeyboardMode) {
    val isChecked = sizingCalculator.getSavedSettings().currentMode == mode

    Surface(
        color = Color.Transparent,
        modifier = Modifier.weight(1.0f).height(54.dp),
        onClick = {
            sizingCalculator.editSavedSettings { settings ->
                settings.copy(
                    currentMode = mode
                ).let {
                    // Set prefersSplit
                    when (mode) {
                        KeyboardMode.Split -> it.copy(prefersSplit = true)
                        KeyboardMode.Regular -> it.copy(prefersSplit = false)
                        else -> it
                    }
                }
            }
        },
        contentColor = if(isChecked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.height(54.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(if(isChecked) checkedIconRes else iconRes),
                    contentDescription = null
                )
                Text(name, style = Typography.labelSmall.copy(fontSize = 14.sp))
            }
        }
    }
}

val KeyboardModeAction = Action(
    icon = R.drawable.keyboard_gear,
    name = R.string.keyboard_modes_action_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        val sizeCalculator = manager.getSizingCalculator()
        object : ActionWindow {
            @Composable
            override fun windowName(): String =
                stringResource(R.string.keyboard_modes_action_title)

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                Column {
                    Row(Modifier.height(ActionBarHeight)) {
                        IconButton(onClick = {
                            manager.closeActionWindow()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                        }
                        Spacer(Modifier.weight(1.0f))
                        TextButton(onClick = {
                            manager.showResizer()
                        }) {
                            Text("Resize Keyboard", style = Typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.W500))
                        }
                    }
                    Row {
                        KeyboardMode(
                            R.drawable.keyboard_regular,
                            R.drawable.keyboard_fill_check,
                            "Standard",
                            sizeCalculator, KeyboardMode.Regular
                        )

                        KeyboardMode(
                            R.drawable.keyboard_left_handed,
                            R.drawable.keyboard_left_handed_fill_check,
                            "One Hand",
                            sizeCalculator, KeyboardMode.OneHanded
                        )

                        KeyboardMode(
                            R.drawable.keyboard_split,
                            R.drawable.keyboard_split_fill_check,
                            "Split",
                            sizeCalculator, KeyboardMode.Split
                        )

                        KeyboardMode(
                            R.drawable.keyboard_float,
                            R.drawable.keyboard_float_fill_check,
                            "Float",
                            sizeCalculator, KeyboardMode.Floating
                        )
                    }
                }
            }

            override fun close() {

            }
        }
    },
    onlyShowAboveKeyboard = true,
    fixedWindowHeight = 54.dp + ActionBarHeight
)