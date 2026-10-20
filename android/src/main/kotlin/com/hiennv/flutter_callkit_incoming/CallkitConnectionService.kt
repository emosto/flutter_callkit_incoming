package com.hiennv.flutter_callkit_incoming

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/** Telecom mirrors call state; the app remains the only SIP/media owner. */
class CallkitConnectionService : ConnectionService() {
    companion object {
        private val connections = mutableMapOf<String, VoxbiConnection>()
        private val pending = mutableSetOf<String>()
        private val desired = mutableMapOf<String, String>()

        fun routeAudio(speaker: Boolean, complete: (Boolean) -> Unit, fail: (String) -> Unit) {
            val id = CallAudioRoutePolicy.connection(desired, connections.keys)
            if (id == null) {
                if (connections.isEmpty()) complete(false)
                else fail("No foreground Telecom connection")
                return
            }
            connections.getValue(id).routeAudio(speaker, complete, fail)
        }

        fun synchronize(context: Context, calls: List<Map<String, String>>) {
            desired.clear()
            calls.forEach { row -> row["id"]?.let { desired[it] = row["state"] ?: "connecting" } }
            connections.keys.toList().filter { !desired.containsKey(it) }.forEach {
                connections.remove(it)?.apply { setDisconnected(DisconnectCause(DisconnectCause.LOCAL)); destroy() }
            }
            desired.forEach { (id, state) ->
                val connection = connections[id]
                if (connection != null) applyState(connection, state)
                // "preparing": answered/dialed, but the app is still setting up media.
                else if (state != "ringing" && state != "preparing" && pending.add(id)) {
                    // The mirror starts after the answer (or dial), so it is placed as an
                    // outgoing call: an incoming one would make Telecom ring an answered
                    // call, taking ringtone focus and switching the audio mode during setup.
                    try {
                        val account = InAppCallManager(context)
                        account.registerPhoneAccount()
                        val extras = Bundle().apply {
                            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account.getPhoneAccountHandle())
                            putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply { putString("voxbiCallId", id) })
                        }
                        (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager)
                            .placeCall(Uri.fromParts(PhoneAccount.SCHEME_SIP, id, null), extras)
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

    override fun onCreateOutgoingConnection(account: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        val id = request.extras?.getString("voxbiCallId") ?: return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        pending.remove(id)
        if (!desired.containsKey(id)) return Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED))
        val connection = VoxbiConnection(id)
        connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD
        connection.setAudioModeIsVoip(true)
        // The placeholder handle only carries the call id; never expose it as an address.
        connection.setAddress(null, TelecomManager.PRESENTATION_UNKNOWN)
        connection.setDialing()
        connections[id] = connection
        // Telecom must receive the connection before setActive/setOnHold.
        android.os.Handler(mainLooper).post { desired[id]?.let { applyState(connection, it) } }
        return connection
    }

    private class VoxbiConnection(private val id: String) : Connection() {
        private var endpoints: List<CallEndpoint> = emptyList()
        override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
            endpoints = availableEndpoints
        }

        fun routeAudio(speaker: Boolean, complete: (Boolean) -> Unit, fail: (String) -> Unit) {
            if (Build.VERSION.SDK_INT >= 34 && endpoints.isNotEmpty()) {
                val byRoute = endpoints.mapNotNull { endpoint ->
                    val route = when (endpoint.endpointType) {
                        CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
                        CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                        CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
                        CallEndpoint.TYPE_EARPIECE -> CallAudioState.ROUTE_EARPIECE
                        else -> null
                    }
                    route?.let { it to endpoint }
                }.toMap()
                val endpoint = byRoute[CallAudioRoutePolicy.select(speaker, byRoute.keys)]
                if (endpoint == null) { fail("Requested call output is unavailable"); return }
                requestCallEndpointChange(endpoint, { task -> android.os.Handler(android.os.Looper.getMainLooper()).post(task) },
                    object : OutcomeReceiver<Void, CallEndpointException> {
                        override fun onResult(result: Void?) { complete(true) }
                        override fun onError(error: CallEndpointException) { fail("Telecom route request failed: ${error.code}") }
                    })
            } else {
                val supported = callAudioState?.supportedRouteMask ?: 0
                val available = setOf(CallAudioState.ROUTE_SPEAKER, CallAudioState.ROUTE_EARPIECE,
                    CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_WIRED_HEADSET).filter { supported and it != 0 }.toSet()
                val route = CallAudioRoutePolicy.select(speaker, available)
                if (route == null) { fail("Requested call output is unavailable"); return }
                @Suppress("DEPRECATION")
                setAudioRoute(route)
                complete(true)
            }
        }
        override fun onHold() = emit(CallkitConstants.ACTION_CALL_TOGGLE_HOLD, mapOf("isOnHold" to true))
        override fun onUnhold() = emit(CallkitConstants.ACTION_CALL_TOGGLE_HOLD, mapOf("isOnHold" to false))
        override fun onDisconnect() = emit(CallkitConstants.ACTION_CALL_ENDED)
        override fun onAnswer() = emit(CallkitConstants.ACTION_CALL_ACCEPT)
        override fun onReject() = emit(CallkitConstants.ACTION_CALL_DECLINE)
        private fun emit(event: String, fields: Map<String, Any> = emptyMap()) {
            FlutterCallkitIncomingPlugin.sendEvent(event, mapOf("id" to id) + fields)
        }
    }


    override fun onCreateOutgoingConnectionFailed(account: PhoneAccountHandle?, request: ConnectionRequest) {
        request.extras?.getString("voxbiCallId")?.let { pending.remove(it) }
        Log.w("CallWaiting", "Telecom connection refused")
    }
}
