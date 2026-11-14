package com.nandox.arcade.controller

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ControlsStore(context: Context) {
    private val prefs = context.getSharedPreferences("controls", Context.MODE_PRIVATE)

    fun load(): KeyMap {
        val overrides = InputAction.entries.mapNotNull { a ->
            val codes = prefs.getStringSet("map_${a.name}", null)?.mapNotNull { it.toIntOrNull() }?.toSet()
            if (codes.isNullOrEmpty()) null else a to codes
        }.toMap()
        return KeyMap(overrides)
    }

    fun save(map: KeyMap) {
        prefs.edit().apply {
            InputAction.entries.forEach { remove("map_${it.name}") }
            map.overrides.forEach { (a, codes) -> putStringSet("map_${a.name}", codes.map { it.toString() }.toSet()) }
        }.apply()
    }
}

class ControlsController(private val store: ControlsStore) {
    var keyMap by mutableStateOf(store.load())
        private set
    var listening by mutableStateOf<InputAction?>(null)

    fun assign(action: InputAction, keyCode: Int) {
        keyMap = keyMap.with(action, keyCode)
        store.save(keyMap)
        listening = null
    }

    fun reset() {
        keyMap = KeyMap()
        store.save(keyMap)
        listening = null
    }
}
