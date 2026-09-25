package com.hiennv.flutter_callkit_incoming

import android.content.Context
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/** Telecom mirrors call state; the app remains the only SIP/media owner. */
class CallkitConnectionService : ConnectionService() {
    companion object {
        private val connections = mutableMapOf<String, Connection>()
        private val pending = mutableSetOf<String>()
        private val desired = mutableMapOf<String, String>()

        fun synchronize(context: Context, calls: List<Map<String, String>>) {
            desired.clear()
            calls.forEach { row -> row["id"]?.let { desired[it] = row["state"] ?: "connecting" } }
            connections.keys.toList().filter { !desired.containsKey(it) }.forEach {
                connections.remove(it)?.apply { setDisconnected(DisconnectCause(DisconnectCause.LOCAL)); destroy() }
            }
            desired.forEach { (id, state) ->
                val connection = connections[id]
                if (connection != null) applyState(connection, state)
                else if (state != "ringing" && pending.add(id)) {
                    try {
                        val account = InAppCallManager(context)
                        account.registerPhoneAccount()
                        val extras = Bundle().apply { putString("voxbiCallId", id) }
                        (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager)
                            .addNewIncomingCall(account.getPhoneAccountHandle(), extras)
                    } catch (error: Exception) {
                        pending.remove(id)
                        Log.w("CallWaiting", "Telecom connection unavailable: ${error.javaClass.simpleName}")
                    }
                }
            }
        }

        private fun applyState(connection: Connection, state: String) {
            when (state) {
                "active" -> connection.setActive()
                "held", "holding" -> connection.setOnHold()
            }
        }
    }

    override fun onCreateIncomingConnection(account: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        val id = request.extras?.getString("voxbiCallId") ?: return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        pending.remove(id)
        if (!desired.containsKey(id)) return Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED))
        val connection = object : Connection() {
            override fun onHold() = emit(CallkitConstants.ACTION_CALL_TOGGLE_HOLD, mapOf("isOnHold" to true))
            override fun onUnhold() = emit(CallkitConstants.ACTION_CALL_TOGGLE_HOLD, mapOf("isOnHold" to false))
            override fun onDisconnect() = emit(CallkitConstants.ACTION_CALL_ENDED)
            override fun onAnswer() = emit(CallkitConstants.ACTION_CALL_ACCEPT)
            override fun onReject() = emit(CallkitConstants.ACTION_CALL_DECLINE)
            private fun emit(event: String, fields: Map<String, Any> = emptyMap()) {
                FlutterCallkitIncomingPlugin.sendEvent(event, mapOf("id" to id) + fields)
            }
        }
        connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD
        connection.setAudioModeIsVoip(true)
        connection.setInitializing()
        connections[id] = connection
        // Telecom must receive the connection before setActive/setOnHold.
        android.os.Handler(mainLooper).post { desired[id]?.let { applyState(connection, it) } }
        return connection
    }

    override fun onCreateIncomingConnectionFailed(account: PhoneAccountHandle?, request: ConnectionRequest) {
        request.extras?.getString("voxbiCallId")?.let { pending.remove(it) }
    }
}
