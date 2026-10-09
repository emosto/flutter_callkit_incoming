import Foundation

/// Only the app's owned calls contribute to diagnostics. Never render identities
/// or caller metadata, including calls belonging to other providers.
struct CallkitSystemCallState {
    let id: UUID
    let connected: Bool
    let held: Bool
    let ended: Bool
}

enum CallkitSystemStateSnapshot {
    static func summary(calls: [CallkitSystemCallState], ownedIDs: Set<UUID>,
                        pendingActions: Int, waitingActions: Int,
                        localHeld: Int, localConnected: Int,
                        holdingSupported: Int) -> String {
        let owned = calls.filter { ownedIDs.contains($0.id) }
        let live = owned.filter { !$0.ended }
        return "owned=\(ownedIDs.count) system=\(live.count) "
            + "connected=\(live.filter { $0.connected }.count) "
            + "held=\(live.filter { $0.held }.count) "
            + "active=\(live.filter { $0.connected && !$0.held }.count) "
            + "ended=\(owned.filter { $0.ended }.count) "
            + "pending=\(pendingActions) waiting=\(waitingActions) "
            + "localHeld=\(localHeld) localConnected=\(localConnected) "
            + "holdingSupported=\(holdingSupported)"
    }
}
