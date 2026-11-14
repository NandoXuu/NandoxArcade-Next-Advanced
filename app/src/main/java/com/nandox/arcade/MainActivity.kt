package com.nandox.arcade

import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.nandox.arcade.saves.Outcome
import com.nandox.arcade.saves.SaveService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nandox.arcade.controller.AnalogNavigator
import com.nandox.arcade.controller.ControlsController
import com.nandox.arcade.controller.ControlsStore
import com.nandox.arcade.controller.InputAction
import com.nandox.arcade.ui.ArcadeApp
import com.nandox.arcade.ui.ArcadeTheme

class MainActivity : ComponentActivity() {
    private lateinit var controls: ControlsController
    private val analog = AnalogNavigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ControlsController(ControlsStore(this))
        enableEdgeToEdge()
        setContent { ArcadeTheme { ArcadeApp(controls) } }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            delay(2000)
            val result = SaveService.autoBackupIfPending(this@MainActivity)
            if (result?.outcome == Outcome.FAILED) {
                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val listening = controls.listening
        if (listening != null && event.keyCode != KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                controls.assign(listening, event.keyCode)
            }
            return true
        }
        if (listening != null) controls.listening = null

        val action = controls.keyMap.actionFor(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (action == InputAction.BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        val navCode = requireNotNull(action.navKeyCode)
        if (navCode == event.keyCode) return super.dispatchKeyEvent(event)
        return super.dispatchKeyEvent(KeyEvent(event.action, navCode))
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.source and InputDevice.SOURCE_JOYSTICK != 0 && ev.action == MotionEvent.ACTION_MOVE) {
            val dir = analog.update(
                ev.getAxisValue(MotionEvent.AXIS_X),
                ev.getAxisValue(MotionEvent.AXIS_Y),
                SystemClock.uptimeMillis(),
            )
            if (dir != null) {
                val code = requireNotNull(dir.navKeyCode)
                super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                return true
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }
}
