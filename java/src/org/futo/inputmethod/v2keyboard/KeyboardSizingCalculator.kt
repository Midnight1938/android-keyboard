package org.futo.inputmethod.v2keyboard

import android.content.Context
import android.graphics.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.window.layout.FoldingFeature
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.FoldStateProvider
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.settings.SettingsValues
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.UixManager
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.utils.ResourceUtils
import kotlin.math.roundToInt

interface KeyboardSizeStateProvider {
    val currentSizeState: KeyboardSizeSettingKind
}

sealed class ComputedKeyboardSize(
    val width: Int,
    val height: Int,
    val padding: Rect,
    val singleRowHeight: Int = height / 4
)

class RegularKeyboardSize(
    width: Int, height: Int, padding: Rect, singleRowHeight: Int = height / 4
) : ComputedKeyboardSize(width, height, padding, singleRowHeight)

class SplitKeyboardSize(
    width: Int, height: Int, padding: Rect, singleRowHeight: Int = height / 4,
    val splitLayoutWidth: Int
) : ComputedKeyboardSize(width, height, padding, singleRowHeight)

enum class OneHandedDirection {
    Left,
    Right
}

val OneHandedDirection.opposite: OneHandedDirection
    get() = when(this) {
        OneHandedDirection.Left -> OneHandedDirection.Right
        OneHandedDirection.Right -> OneHandedDirection.Left
    }

class OneHandedKeyboardSize(
    width: Int, height: Int, padding: Rect, singleRowHeight: Int = height / 4,
    val layoutWidth: Int, val direction: OneHandedDirection
) : ComputedKeyboardSize(width, height, padding, singleRowHeight)

class FloatingKeyboardSize(
    width: Int, height: Int, padding: Rect, singleRowHeight: Int = height / 4,
    val bottomOrigin: Pair<Int, Int>
): ComputedKeyboardSize(width, height, padding, singleRowHeight)

val ComputedKeyboardSize.totalKeyboardWidth: Int
    get() = when(this) {
        is FloatingKeyboardSize -> width - padding.left - padding.right
        is OneHandedKeyboardSize -> layoutWidth
        is RegularKeyboardSize -> width - padding.left - padding.right
        is SplitKeyboardSize -> width - padding.left - padding.right
    }

enum class KeyboardMode {
    Regular,
    Split,
    OneHanded,
    Floating
}


object DpRectSerializer : KSerializer<DpRect> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DpRect") {
        element<Float>("left")
        element<Float>("top")
        element<Float>("right")
        element<Float>("bottom")
    }

    override fun serialize(encoder: Encoder, value: DpRect) {
        encoder.encodeStructure(descriptor) {
            encodeFloatElement(descriptor, 0, value.left.value)
            encodeFloatElement(descriptor, 1, value.top.value)
            encodeFloatElement(descriptor, 2, value.right.value)
            encodeFloatElement(descriptor, 3, value.bottom.value)
        }
    }

    override fun deserialize(decoder: Decoder): DpRect {
        return decoder.decodeStructure(descriptor) {
            var left = 0.0f
            var top = 0.0f
            var right = 0.0f
            var bottom = 0.0f

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> left = decodeFloatElement(descriptor, 0)
                    1 -> top = decodeFloatElement(descriptor, 1)
                    2 -> right = decodeFloatElement(descriptor, 2)
                    3 -> bottom = decodeFloatElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            DpRect(left = left.dp, top = top.dp, right = right.dp, bottom = bottom.dp)
        }
    }
}


typealias SDpRect = @Serializable(with = DpRectSerializer::class) DpRect


@Serializable
data class SavedKeyboardSizingSettings(
    val currentMode: KeyboardMode,
    val heightMultiplier: Float,
    val heightAdditionDp: Float = 0.0f,
    val paddingDp: SDpRect,

    // Split
    val splitWidthFraction: Float,
    val splitPaddingDp: SDpRect,
    val splitHeightAdditionDp: Float = 0.0f,
    val prefersSplit: Boolean,

    /** One handed, values with respect to left handed mode:
     * * left = padding
     * * right = width + padding
     * * bottom = padding for bottom */
    val oneHandedRectDp: SDpRect,
    val oneHandedDirection: OneHandedDirection,
    val oneHandedHeightAdditionDp: Float = 0.0f,

    // Floating
    // bottom left of the floating keyboard, relative to bottom left of screen, .second is Y up
    val floatingBottomOriginDp: Pair<Float, Float>,
    val floatingWidthDp: Float,
    val floatingHeightDp: Float,
) {
    fun toJsonString(): String =
        Json.encodeToString(this)

    companion object {
        @JvmStatic
        fun fromJsonString(s: String): SavedKeyboardSizingSettings? =
            try {
                Json.decodeFromString(s)
            } catch (e: Exception) {
                //e.printStackTrace()
                null
            }
    }
}

enum class KeyboardSizeSettingKind {
    Portrait,
    Landscape,
    FoldableInnerDisplay
}

val DefaultKeyboardSettings = mapOf(
    KeyboardSizeSettingKind.Portrait to SavedKeyboardSizingSettings(
        currentMode = KeyboardMode.Regular,
        heightMultiplier = 1.0f,
        paddingDp = DpRect(2.dp, 4.dp, 2.dp, 10.dp),
        splitPaddingDp = DpRect(2.dp, 4.dp, 2.dp, 10.dp),
        splitWidthFraction = 4.0f / 5.0f,
        oneHandedDirection = OneHandedDirection.Right,
        oneHandedRectDp = DpRect(4.dp, 4.dp, 364.dp, 30.dp),
        floatingBottomOriginDp = Pair(0.0f, 0.0f),
        floatingHeightDp = 240.0f,
        floatingWidthDp = 360.0f,
        prefersSplit = false
    ),

    KeyboardSizeSettingKind.Landscape to SavedKeyboardSizingSettings(
        currentMode = KeyboardMode.Split,
        heightMultiplier = 0.9f,
        paddingDp = DpRect(8.dp, 2.dp, 8.dp, 2.dp),
        splitPaddingDp = DpRect(8.dp, 2.dp, 8.dp, 2.dp),
        splitWidthFraction = 3.0f / 5.0f,
        oneHandedDirection = OneHandedDirection.Right,
        oneHandedRectDp = DpRect(4.dp, 4.dp, 364.dp, 30.dp),
        floatingBottomOriginDp = Pair(0.0f, 0.0f),
        floatingHeightDp = 240.0f,
        floatingWidthDp = 360.0f,
        prefersSplit = true
    ),

    KeyboardSizeSettingKind.FoldableInnerDisplay to SavedKeyboardSizingSettings(
        currentMode = KeyboardMode.Split,
        heightMultiplier = 0.67f,
        paddingDp = DpRect(44.dp, 4.dp, 44.dp, 8.dp),
        splitPaddingDp = DpRect(44.dp, 4.dp, 44.dp, 8.dp),
        splitWidthFraction = 3.0f / 5.0f,
        oneHandedDirection = OneHandedDirection.Right,
        oneHandedRectDp = DpRect(4.dp, 4.dp, 364.dp, 30.dp),
        floatingBottomOriginDp = Pair(0.0f, 0.0f),
        floatingHeightDp = 240.0f,
        floatingWidthDp = 360.0f,
        prefersSplit = true
    ),
)

val KeyboardSettings = mapOf(
    KeyboardSizeSettingKind.Portrait to SettingsKey(
        stringPreferencesKey("keyboard_settings_portrait"), ""),
    KeyboardSizeSettingKind.Landscape to SettingsKey(
        stringPreferencesKey("keyboard_settings_landscape"), ""),
    KeyboardSizeSettingKind.FoldableInnerDisplay to SettingsKey(
        stringPreferencesKey("keyboard_settings_fold"), ""),
)

class KeyboardSizingCalculator(val context: Context, val uixManager: UixManager) {
    val sizeStateProvider = context as KeyboardSizeStateProvider
    val foldStateProvider = context as FoldStateProvider

    private fun dp(v: Number): Int =
        (v.toFloat() * context.resources.displayMetrics.density).toInt()

    private fun dp(v: Dp): Int = dp(v.value)

    private fun dp(v: Rect): Rect =
        Rect(dp(v.left), dp(v.top), dp(v.right), dp(v.bottom))

    private fun dp(v: DpRect): Rect =
        Rect(dp(v.left), dp(v.top), dp(v.right), dp(v.bottom))

    fun getSavedSettings(): SavedKeyboardSizingSettings =
        SavedKeyboardSizingSettings.fromJsonString(context.getSettingBlocking(
            KeyboardSettings[sizeStateProvider.currentSizeState]!!
        )) ?: DefaultKeyboardSettings[sizeStateProvider.currentSizeState]!!

    fun editSavedSettings(transform: (SavedKeyboardSizingSettings) -> SavedKeyboardSizingSettings) {
        val sizeState = sizeStateProvider.currentSizeState

        val savedSettings = SavedKeyboardSizingSettings.fromJsonString(context.getSettingBlocking(
            KeyboardSettings[sizeState]!!
        )) ?: DefaultKeyboardSettings[sizeState]!!

        val transformed = transform(savedSettings)

        if(transformed != savedSettings) {
            context.setSettingBlocking(KeyboardSettings[sizeState]!!.key, transformed.toJsonString())
        }
    }

    fun resetCurrentMode() {
        val defaultSettings = DefaultKeyboardSettings[sizeStateProvider.currentSizeState]!!
        editSavedSettings {
            when(it.currentMode) {
                KeyboardMode.Regular -> it.copy(
                    heightMultiplier = defaultSettings.heightMultiplier,
                    heightAdditionDp = defaultSettings.heightAdditionDp,
                    paddingDp = defaultSettings.paddingDp
                )
                KeyboardMode.Split -> it.copy(
                    splitPaddingDp = defaultSettings.splitPaddingDp,
                    splitHeightAdditionDp = defaultSettings.splitHeightAdditionDp,
                    splitWidthFraction = defaultSettings.splitWidthFraction
                )
                KeyboardMode.OneHanded -> it.copy(
                    oneHandedRectDp = defaultSettings.oneHandedRectDp,
                    oneHandedHeightAdditionDp = defaultSettings.oneHandedHeightAdditionDp
                )
                KeyboardMode.Floating -> it.copy(
                    floatingHeightDp = defaultSettings.floatingHeightDp,
                    floatingWidthDp = defaultSettings.floatingWidthDp,
                    floatingBottomOriginDp = defaultSettings.floatingBottomOriginDp
                )
            }
        }
    }

    fun exitOneHandedMode() = editSavedSettings { it.copy(
        currentMode = if(it.prefersSplit) KeyboardMode.Split else KeyboardMode.Regular
    ) }

    fun calculate(layoutName: String, settings: SettingsValues): ComputedKeyboardSize {
        val savedSettings = getSavedSettings()

        val layout = LayoutManager.getLayout(context, layoutName)
        val effectiveRowCount = layout.effectiveRows.size

        val displayMetrics = context.resources.displayMetrics

        val heightAddition = when(savedSettings.currentMode) {
            KeyboardMode.Regular -> dp(savedSettings.heightAdditionDp)
            KeyboardMode.Split -> dp(savedSettings.splitHeightAdditionDp)
            KeyboardMode.OneHanded -> dp(savedSettings.oneHandedHeightAdditionDp)
            KeyboardMode.Floating -> 0
        }

        val padding = when(savedSettings.currentMode) {
            KeyboardMode.Regular -> dp(savedSettings.paddingDp)
            KeyboardMode.Split -> dp(savedSettings.splitPaddingDp)
            KeyboardMode.OneHanded -> dp(savedSettings.oneHandedRectDp).let { rect ->
                when(savedSettings.oneHandedDirection) {
                    OneHandedDirection.Left -> Rect(rect.left, rect.top, rect.left, rect.bottom)
                    OneHandedDirection.Right -> Rect(rect.left, rect.top, rect.left, rect.bottom)
                }
            }
            KeyboardMode.Floating -> dp(Rect(8,8,8,8))
        }

        val singularRowHeight = ((ResourceUtils.getDefaultKeyboardHeight(context.resources) + heightAddition - padding.bottom) / 4.0) *
                savedSettings.heightMultiplier

        val numRows = 4.0 +
                ((effectiveRowCount - 5) / 2.0).coerceAtLeast(0.0) +
                if(settings.mIsNumberRowEnabled) { 0.5 } else { 0.0 } +
                if(settings.mIsArrowRowEnabled)  { 0.8 } else { 0.0 }

        val recommendedHeight = numRows * singularRowHeight + padding.bottom


        val foldState = foldStateProvider.foldState.feature

        val window = (context as LatinIME).window.window
        val width = ResourceUtils.getDefaultKeyboardWidth(window, context.resources)

        return when {
            // Special case: 50% screen height no matter the row count or settings
            foldState != null && foldState.state == FoldingFeature.State.HALF_OPENED && foldState.orientation == FoldingFeature.Orientation.HORIZONTAL -> {
                val totalHeight = displayMetrics.heightPixels / 2 - (displayMetrics.density * 80.0f).toInt()
                val singleRowHeight = totalHeight / numRows
                SplitKeyboardSize(
                    width = width,
                    height = totalHeight,
                    singleRowHeight = singleRowHeight.roundToInt(),
                    padding = Rect(
                        (displayMetrics.density * 44.0f).roundToInt(),
                        (displayMetrics.density * 50.0f).roundToInt(),
                        (displayMetrics.density * 44.0f).roundToInt(),
                        (displayMetrics.density * 12.0f).roundToInt(),
                    ),
                    splitLayoutWidth = displayMetrics.widthPixels * 3 / 5
                )
            }

            savedSettings.currentMode == KeyboardMode.Split ->
                SplitKeyboardSize(
                    width = width,
                    height = recommendedHeight.roundToInt(),
                    singleRowHeight = singularRowHeight.roundToInt(),
                    padding = padding,
                    splitLayoutWidth = (displayMetrics.widthPixels * savedSettings.splitWidthFraction).toInt()
                )

            savedSettings.currentMode == KeyboardMode.OneHanded ->
                OneHandedKeyboardSize(
                    width = width,
                    height = recommendedHeight.roundToInt(),
                    singleRowHeight = singularRowHeight.roundToInt(),
                    padding = padding,
                    layoutWidth = dp(savedSettings.oneHandedRectDp.width).coerceAtMost(displayMetrics.widthPixels * 9 / 10),
                    direction = savedSettings.oneHandedDirection
                )

            savedSettings.currentMode == KeyboardMode.Floating -> {
                val singularRowHeightFloat = dp(savedSettings.floatingHeightDp) / 4.0f
                val recommendedHeightFloat = singularRowHeightFloat * numRows
                FloatingKeyboardSize(
                    bottomOrigin = Pair(
                        dp(savedSettings.floatingBottomOriginDp.first),
                        dp(savedSettings.floatingBottomOriginDp.second)
                    ),
                    width = dp(savedSettings.floatingWidthDp),
                    height = recommendedHeightFloat.toInt(),
                    singleRowHeight = singularRowHeightFloat.roundToInt(),
                    padding = padding
                )
            }

            else ->
                RegularKeyboardSize(
                    width = width,
                    height = recommendedHeight.roundToInt(),
                    singleRowHeight = singularRowHeight.roundToInt(),
                    padding = padding,
                )
        }
    }

    fun calculateGap(): Float {
        val displayMetrics = context.resources.displayMetrics

        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        val heightDp = displayMetrics.heightPixels / displayMetrics.density

        val minDp = Math.min(widthDp, heightDp)

        return (minDp / 100.0f).coerceIn(3.0f, 6.0f)
    }

    fun calculateSuggestionBarHeightDp(): Float {
        return 40.0f
    }

    fun calculateTotalActionBarHeightPx(): Int =
        when {
            uixManager.actionsExpanded && (uixManager.currWindowActionWindow == null) -> dp(2 * calculateSuggestionBarHeightDp())
            else -> dp(calculateSuggestionBarHeightDp())
        }
}