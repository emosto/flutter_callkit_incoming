package com.hiennv.flutter_callkit_incoming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/** Process-owned across Flutter engines; never stores SIP credentials. */
internal object CallWaitingController {
    private val states = mutableMapOf<String, String>()
    private val dismissed = mutableSetOf<String>()
    private val waiting = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var cueRunning = false
    private var audioInterrupted = false
    private var labels: Map<String, String> = emptyMap()
    private val cue = object : Runnable {
        override fun run() {
            if (!cueRunning) return
            try {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 15)
                tone?.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 400)
            } catch (error: RuntimeException) {
                Log.w("CallWaiting", "Cue unavailable")
            }
            handler.postDelayed(this, 10000)
        }
    }

    fun update(context: Context, calls: List<Map<String, String>>, text: Map<String, String>?, interrupted: Boolean? = null) {
        if (interrupted != null) audioInterrupted = interrupted
        if (text != null) labels = text
        states.clear()
        calls.forEach { call -> call["id"]?.let { states[it] = call["state"] ?: "connecting" } }
        waiting.toList().filter { states[it] != "ringing" }.forEach { clear(context, it) }
        val play = !audioInterrupted && states.containsValue("active") && states.containsValue("ringing")
        if (play && !cueRunning) {
            cueRunning = true
            handler.post(cue)
        } else if (!play) {
            cueRunning = false
            handler.removeCallbacks(cue)
            tone?.release()
            tone = null
        }
        CallkitConnectionService.synchronize(context, calls)
    }

    /** True when normal full-screen/ringing presentation must be skipped. */
    fun incoming(context: Context, data: Bundle): Boolean {
        val id = data.getString(CallkitConstants.EXTRA_CALLKIT_ID, "")
        if (id.isEmpty()) return false
        val otherCount = states.keys.count { it != id }
        val enabled = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getBoolean("flutter.callWaitingEnabled", true)
        if (CallWaitingPolicy.dismiss(otherCount, enabled, dismissed.contains(id))) {
            dismissed.add(id)
            clear(context, id)
            Log.i("CallWaiting", "PBX decline suppressed reason=disabledOrFull")
            return true
        }
        if (otherCount == 0) return false
        states[id] = "ringing"
        val firstAlert = waiting.add(id)
        if (firstAlert) handler.postDelayed({
            if (waiting.contains(id) && states[id] == "ringing") {
                context.sendBroadcast(CallkitIncomingBroadcastReceiver.getIntentTimeout(context, data))
            }
        }, data.getLong(CallkitConstants.EXTRA_CALLKIT_DURATION, 30000L))
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel("voxbi_call_waiting", "Call waiting", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
        })
        @Suppress("DEPRECATION")
        val profile = (data.getSerializable(CallkitConstants.EXTRA_CALLKIT_EXTRA) as? Map<*, *>)
            ?.get("username")?.toString().orEmpty()
        val waitingLabel = labels["waiting"] ?: "Call waiting"
        val notification = NotificationCompat.Builder(context, "voxbi_call_waiting")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(data.getString(CallkitConstants.EXTRA_CALLKIT_NAME_CALLER, "Voxbi"))
            .setContentText(if (profile.isEmpty()) waitingLabel else "$waitingLabel · $profile")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true).setSilent(true)
        fun action(title: String, reject: Boolean, endCurrent: Boolean, request: Int) {
            val payload = Bundle(data).apply { putBoolean("waitingEndCurrent", endCurrent) }
            val intent = if (reject) CallkitIncomingBroadcastReceiver.getIntentDecline(context, payload)
                else CallkitIncomingBroadcastReceiver.getIntentAccept(context, payload)
            val pending = PendingIntent.getBroadcast(context, id.hashCode() + request, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notification.addAction(0, title, pending)
        }
        action(labels["reject"] ?: "Reject", true, false, 1)
        action(labels["holdAnswer"] ?: "Hold & Answer", false, false, 2)
        action(labels["endAnswer"] ?: "End & Answer", false, true, 3)
        notifications.notify(id.hashCode(), notification.build())
        update(context, states.map { mapOf("id" to it.key, "state" to it.value) }, null)
        return true
    }

    fun hasOtherCall(id: String) = states.keys.any { it != id }
    fun isWaiting(id: String) = waiting.contains(id)
    fun isDismissed(id: String) = dismissed.contains(id)
    fun clear(context: Context, id: String) {
        waiting.remove(id)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.hashCode())
    }
}
