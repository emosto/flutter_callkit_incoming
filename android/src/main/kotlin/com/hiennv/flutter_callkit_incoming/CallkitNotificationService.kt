package com.hiennv.flutter_callkit_incoming

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

class CallkitNotificationService : Service() {

    companion object {

        private val ActionForeground = listOf(
            CallkitConstants.ACTION_CALL_START,
            CallkitConstants.ACTION_CALL_ACCEPT
        )


        fun startServiceWithAction(context: Context, action: String, data: Bundle?) {
            if (action !in ActionForeground || data == null) return
            if (!data.getBoolean(CallkitConstants.EXTRA_CALLKIT_CALLING_SHOW, true)) {
                if (action == CallkitConstants.ACTION_CALL_ACCEPT) {
                    notificationManager(context).clearIncomingNotification(data, true)
                }
                stopService(context)
                return
            }
            val intent = Intent(context, CallkitNotificationService::class.java).apply {
                this.action = action
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: IllegalStateException) {
                Log.w("CallkitNotification", "Foreground service start not allowed")
                clearAcceptedNotification(context, action, data)
            } catch (error: SecurityException) {
                Log.w("CallkitNotification", "Foreground service permission denied")
                clearAcceptedNotification(context, action, data)
            }
        }

        private fun clearAcceptedNotification(context: Context, action: String, data: Bundle) {
            if (action == CallkitConstants.ACTION_CALL_ACCEPT) {
                notificationManager(context).clearIncomingNotification(data, true)
            }
        }

        private fun notificationManager(context: Context): CallkitNotificationManager =
            FlutterCallkitIncomingPlugin.getInstance()?.getCallkitNotificationManager()
                ?: CallkitNotificationManager(context.applicationContext, null)

        fun stopService(context: Context) {
            val intent = Intent(context, CallkitNotificationService::class.java)
            context.stopService(intent)
        }

    }

    // A receiver can start this service before a Flutter engine/plugin exists.
    private val fallbackNotificationManager by lazy {
        CallkitNotificationManager(applicationContext, null)
    }
    private val callkitNotificationManager: CallkitNotificationManager
        get() = FlutterCallkitIncomingPlugin.getInstance()?.getCallkitNotificationManager()
            ?: fallbackNotificationManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val data = intent?.getBundleExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)
        if (action !in ActionForeground || data == null ||
            !data.getBoolean(CallkitConstants.EXTRA_CALLKIT_CALLING_SHOW, true)) {
            if (action == CallkitConstants.ACTION_CALL_ACCEPT && data != null) {
                callkitNotificationManager.clearIncomingNotification(data, true)
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            callkitNotificationManager.createNotificationChanel(data)
            // Promote before doing secondary notification cleanup.
            showOngoingCallNotification(data)
        } catch (error: IllegalStateException) {
            Log.w("CallkitNotification", "Foreground promotion not allowed")
            stopSelf(startId)
        } catch (error: SecurityException) {
            Log.w("CallkitNotification", "Foreground promotion permission denied")
            stopSelf(startId)
        } finally {
            if (action == CallkitConstants.ACTION_CALL_ACCEPT) {
                callkitNotificationManager.clearIncomingNotification(data, true)
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun showOngoingCallNotification(bundle: Bundle) {

        val callkitNotification =
            this.callkitNotificationManager.getOnGoingCallNotification(bundle, false)
        if (callkitNotification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    callkitNotification.id,
                    callkitNotification.notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(callkitNotification.id, callkitNotification.notification)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        callkitNotificationManager.destroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }else {
            stopForeground(true)
        }
        stopSelf()
    }



}

