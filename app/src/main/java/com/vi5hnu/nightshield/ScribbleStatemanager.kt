package com.vi5hnu.nightshield
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.*


object NightShieldManager {
    val activeFieldFlags = (WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            or WindowManager.LayoutParams.FLAG_FULLSCREEN
            or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN // Keep this for better control
            )
    val inactiveFieldFlags=(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            or WindowManager.LayoutParams.FLAG_FULLSCREEN
            or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN // Keep this for better control
            )
    // Computed states for undo/redo availability
    private val _shakeActive= MutableStateFlow(true);
    private val _allowShake= MutableStateFlow(true);


    private val _isCanvasColorPickerVisible= MutableStateFlow(false);
    private val _canvasColor= MutableStateFlow(Color(0x80FFA500));

    val allowShake: StateFlow<Boolean> = _allowShake.asStateFlow();
    val shakeActive: StateFlow<Boolean> = _shakeActive.asStateFlow();

    val isCanvasColorPickerVisible: StateFlow<Boolean> = _isCanvasColorPickerVisible.asStateFlow();
    val canvasColor: StateFlow<Color> = _canvasColor.asStateFlow();

    fun setAllowShake(allowShake: Boolean){
        _allowShake.value=allowShake;
    }

    fun setIsCanvasColorPickerVisible(visible: Boolean){
        _isCanvasColorPickerVisible.value=visible;
    }

    fun setCanvasColor(color:Color){
        _canvasColor.value=color;
    }
}
