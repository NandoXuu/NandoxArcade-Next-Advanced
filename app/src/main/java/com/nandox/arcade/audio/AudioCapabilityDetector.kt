package com.nandox.arcade.audio

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.Process

/** Descobre, no próprio aparelho, o que o áudio consegue de fato fazer. Nada é presumido. */
object AudioCapabilityDetector {
    @Volatile private var cached: AudioCapabilities? = null

    fun detect(context: Context): AudioCapabilities {
        cached?.let { return it }
        val app = context.applicationContext
        val pm = app.packageManager

        val lowLatencyFeature = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        // O LibretroDroid só usa o fluxo de baixa latência quando o AAudio é recomendado (Android 8.1+).
        val lowLatencyBackend = lowLatencyFeature && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

        val sustained = runCatching {
            (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isSustainedPerformanceModeSupported
        }.getOrDefault(false)

        val uiMode = app.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTv = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val rate = runCatching { am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() }.getOrNull()
        val frames = runCatching { am?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() }.getOrNull()
        val devices = runCatching {
            am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().map { deviceName(it.type) }.distinct()
        }.getOrDefault(emptyList())

        val caps = AudioCapabilities(
            lowLatencyBackend = lowLatencyBackend,
            priorityBoost = probePriorityBoost(),
            sustainedPerformance = sustained,
            minimalPostProcessing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            isTv = isTv,
            is64Bit = Process.is64Bit(),
            outputSampleRate = rate,
            outputFramesPerBuffer = frames,
            outputDevices = devices,
        )
        cached = caps
        return caps
    }

    /** Testa de verdade: pede prioridade de áudio a uma thread descartável e confere se o sistema aceitou. */
    private fun probePriorityBoost(): Boolean {
        var ok = false
        val probe = Thread {
            try {
                val tid = Process.myTid()
                Process.setThreadPriority(tid, AudioPriority.AUDIO)
                ok = Process.getThreadPriority(tid) <= AudioPriority.AUDIO
            } catch (_: Throwable) {
                ok = false
            }
        }
        probe.start()
        probe.join(1000)
        return ok
    }

    private fun deviceName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (latência alta)"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com fio"
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Saída de linha"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        else -> "Tipo $type"
    }
}
