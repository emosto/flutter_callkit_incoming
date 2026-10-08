import Foundation
import XCTest
@testable import AnswerAudio

final class IncomingAnswerAudioGateTests: XCTestCase {
    func testOldTimeoutCannotCancelRecoveryWaitingForNewActivation() {
        let gate = IncomingAnswerAudioGate()
        let id = UUID()
        var firstResults: [String?] = []
        gate.prepare(id) { firstResults.append($0) }
        let oldGeneration = gate.generation(id)!
        gate.didAnswer(id)
        gate.didActivate()
        XCTAssertEqual(firstResults.count, 1)
        gate.didDeactivate()
        var recoveryResults: [String?] = []
        gate.prepare(id) { recoveryResults.append($0) }
        XCTAssertFalse(gate.cancel(id, reason: "audio_activation_timeout", generation: oldGeneration))
        XCTAssertTrue(recoveryResults.isEmpty)
        gate.didActivate()
        XCTAssertEqual(recoveryResults.count, 1)
        XCTAssertNil(recoveryResults[0])
    }

    func testAnswerWaitsForRealAudioActivation() {
        let gate = IncomingAnswerAudioGate()
        let id = UUID()
        var results: [String?] = []
        gate.prepare(id) { results.append($0) }
        gate.didAnswer(id)
        XCTAssertTrue(results.isEmpty)
        gate.didActivate()
        XCTAssertEqual(results.count, 1)
        XCTAssertNil(results[0])
        gate.didActivate()
        XCTAssertEqual(results.count, 1)
    }

    func testAlreadyActiveAudioStillRequiresThisAnswerAcknowledgment() {
        let gate = IncomingAnswerAudioGate()
        let id = UUID()
        var ready = false
        gate.didActivate()
        gate.prepare(id) { _ in ready = true }
        XCTAssertFalse(ready)
        gate.didAnswer(id)
        XCTAssertTrue(ready)
    }

    func testActivationAndAnswerBeforeWaitAreNotLost() {
        let gate = IncomingAnswerAudioGate()
        let id = UUID()
        gate.didAnswer(id)
        gate.didActivate()
        var ready = false
        gate.prepare(id) { _ in ready = true }
        XCTAssertTrue(ready)
    }

    func testDuplicatesShareOneRequestAndCompleteOnce() {
        let gate = IncomingAnswerAudioGate()
        let id = UUID()
        var count = 0
        XCTAssertTrue(gate.prepare(id) { _ in count += 1 })
        XCTAssertFalse(gate.prepare(id) { _ in count += 1 })
        gate.didAnswer(id)
        gate.didActivate()
        gate.didAnswer(id)
        gate.didActivate()
        XCTAssertEqual(count, 2)
    }

    func testEndedOrTimedOutCallCannotStartMediaOnLateActivation() {
        for reason in ["call_ended", "audio_activation_timeout", "answer_request_failed"] {
            let gate = IncomingAnswerAudioGate()
            let id = UUID()
            var results: [String?] = []
            gate.prepare(id) { results.append($0) }
            gate.didAnswer(id)
            gate.cancel(id, reason: reason)
            gate.didActivate()
            XCTAssertEqual(results.count, 1)
            XCTAssertEqual(results[0], reason)
            XCTAssertFalse(gate.hasAnswered(id))
        }
    }

    func testHeldCallDeactivationRequiresNewActivation() {
        let gate = IncomingAnswerAudioGate()
        gate.didActivate()
        gate.didDeactivate()
        let id = UUID()
        var ready = false
        gate.prepare(id) { _ in ready = true }
        gate.didAnswer(id)
        XCTAssertFalse(ready)
        gate.didActivate()
        XCTAssertTrue(ready)
    }

    func testResetFailsAllWaitersAndClearsAudioState() {
        let gate = IncomingAnswerAudioGate()
        gate.didActivate()
        var results: [String?] = []
        gate.prepare(UUID()) { results.append($0) }
        gate.prepare(UUID()) { results.append($0) }
        gate.reset()
        XCTAssertEqual(results.compactMap { $0 }, ["provider_reset", "provider_reset"])
        let id = UUID()
        gate.prepare(id) { results.append($0) }
        gate.didAnswer(id)
        XCTAssertEqual(results.count, 2)
        gate.didActivate()
        XCTAssertEqual(results.count, 3)
        XCTAssertNil(results[2])
    }
}
