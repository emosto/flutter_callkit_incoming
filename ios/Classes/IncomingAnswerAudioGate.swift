import Foundation

/// Main-queue state for the answer handshake. Media needs BOTH an acknowledged
/// answer and real provider audio activation, in either callback order.
final class IncomingAnswerAudioGate {
    private var waiting: [UUID: [(String?) -> Void]] = [:]
    private var answered = Set<UUID>()
    private var audioActive = false
    private var nextGeneration = 0
    private var generations: [UUID: Int] = [:]

    func isWaiting(_ id: UUID) -> Bool { waiting[id] != nil }
    func hasAnswered(_ id: UUID) -> Bool { answered.contains(id) }
    func generation(_ id: UUID) -> Int? { generations[id] }

    /// Returns true only for the first waiter; it owns the native request/timer.
    @discardableResult
    func prepare(_ id: UUID, completion: @escaping (String?) -> Void) -> Bool {
        let first = waiting[id] == nil
        if first {
            nextGeneration += 1
            generations[id] = nextGeneration
        }
        waiting[id, default: []].append(completion)
        resolveIfReady(id)
        return first
    }

    func didAnswer(_ id: UUID) {
        answered.insert(id)
        resolveIfReady(id)
    }

    func didActivate() {
        audioActive = true
        for id in Array(waiting.keys) { resolveIfReady(id) }
    }

    func didDeactivate() { audioActive = false }

    @discardableResult
    func cancel(_ id: UUID, reason: String, generation: Int? = nil) -> Bool {
        if let generation = generation, generations[id] != generation { return false }
        generations.removeValue(forKey: id)
        answered.remove(id)
        let callbacks = waiting.removeValue(forKey: id) ?? []
        callbacks.forEach { $0(reason) }
        return true
    }

    func reset() {
        audioActive = false
        answered.removeAll()
        let callbacks = waiting.values.flatMap { $0 }
        waiting.removeAll()
        generations.removeAll()
        callbacks.forEach { $0("provider_reset") }
    }

    private func resolveIfReady(_ id: UUID) {
        guard audioActive, answered.contains(id) else { return }
        generations.removeValue(forKey: id)
        let callbacks = waiting.removeValue(forKey: id) ?? []
        callbacks.forEach { $0(nil) }
    }
}
