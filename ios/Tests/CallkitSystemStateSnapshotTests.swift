import Foundation
import XCTest
@testable import AnswerAudio

final class CallkitSystemStateSnapshotTests: XCTestCase {
    private func summary(_ calls: [CallkitSystemCallState], owned: Set<UUID>) -> String {
        CallkitSystemStateSnapshot.summary(calls: calls, ownedIDs: owned,
            pendingActions: 0, waitingActions: 0, localHeld: 1,
            localConnected: 0, holdingSupported: owned.count)
    }

    func testTwoConnectedCallsWithOneHeldAreDistinguishedFromLocalModel() {
        let first = UUID(), second = UUID()
        let text = summary([
            .init(id: first, connected: true, held: true, ended: false),
            .init(id: second, connected: true, held: false, ended: false)
        ], owned: [first, second])
        XCTAssertTrue(text.contains("system=2 connected=2 held=1 active=1 ended=0"))
        XCTAssertTrue(text.contains("localHeld=1 localConnected=0 holdingSupported=2"))
        XCTAssertFalse(text.contains(first.uuidString))
        XCTAssertFalse(text.contains(second.uuidString))
    }

    func testOtherProvidersDoNotAffectOwnedCallDiagnostics() {
        let owned = UUID(), external = UUID()
        let ownCall = CallkitSystemCallState(id: owned, connected: true, held: false, ended: false)
        XCTAssertEqual(summary([ownCall], owned: [owned]), summary([
            ownCall,
            .init(id: external, connected: true, held: true, ended: false)
        ], owned: [owned]))
    }

    func testEndedAndConnectingCallsAreNotCountedAsActive() {
        let ended = UUID(), connecting = UUID(), missing = UUID()
        let text = summary([
            .init(id: ended, connected: true, held: true, ended: true),
            .init(id: connecting, connected: false, held: false, ended: false)
        ], owned: [ended, connecting, missing])
        XCTAssertTrue(text.contains("owned=3 system=1 connected=0 held=0 active=0 ended=1"))
    }
}
