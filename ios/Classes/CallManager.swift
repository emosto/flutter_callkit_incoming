//
//  CallManager.swift
//  flutter_callkit_incoming
//
//  Created by Hien Nguyen on 07/10/2021.
//

import Foundation
import CallKit

@available(iOS 10.0, *)
class CallManager: NSObject, CXCallObserverDelegate {
    
    private let callController = CXCallController()
    private var sharedProvider: CXProvider? = nil
    private(set) var calls = [Call]()

    var systemStateDidChange: (() -> Void)?

    override init() {
        super.init()
        callController.callObserver.setDelegate(self, queue: .main)
    }

    func callObserver(_ callObserver: CXCallObserver, callChanged call: CXCall) {
        systemStateDidChange?()
    }

    func logSystemState(_ context: String, pendingActions: Int, waitingActions: Int) {
        let snapshot = CallkitSystemStateSnapshot.summary(
            calls: callController.callObserver.calls.map {
                CallkitSystemCallState(id: $0.uuid, connected: $0.hasConnected,
                                      held: $0.isOnHold, ended: $0.hasEnded)
            },
            ownedIDs: Set(calls.map { $0.uuid }),
            pendingActions: pendingActions, waitingActions: waitingActions,
            localHeld: calls.filter { $0.isOnHold }.count,
            localConnected: calls.filter { $0.hasConnected }.count,
            holdingSupported: calls.filter { $0.data.supportsHolding }.count)
        NSLog("VOIP-CALL: nativeState=%@ %@", context, snapshot)
    }
    
    
    func setSharedProvider(_ sharedProvider: CXProvider) {
        self.sharedProvider = sharedProvider
    }
    
    func startCall(_ data: Data, completion: ((Bool) -> Void)? = nil) {
        let handle = CXHandle(type: self.getHandleType(data.handleType), value: data.getEncryptHandle())
        let uuid = UUID(uuidString: data.uuid)
        let startCallAction = CXStartCallAction(call: uuid!, handle: handle)
        startCallAction.isVideo = data.type > 0
        let callTransaction = CXTransaction()
        callTransaction.addAction(startCallAction)
        //requestCall
        self.requestCall(callTransaction, action: "startCall", completion: { success in
            completion?(success)
            guard success else { return }
            let callUpdate = CXCallUpdate()
            callUpdate.remoteHandle = handle
            callUpdate.supportsDTMF = data.supportsDTMF
            callUpdate.supportsHolding = data.supportsHolding
            callUpdate.supportsGrouping = data.supportsGrouping
            callUpdate.supportsUngrouping = data.supportsUngrouping
            callUpdate.hasVideo = data.type > 0 ? true : false
            callUpdate.localizedCallerName = data.nameCaller
            self.sharedProvider?.reportCall(with: uuid!, updated: callUpdate)
        })
    }
    
    func muteCall(call: Call, isMuted: Bool) {
        let muteAction = CXSetMutedCallAction(call: call.uuid, muted: isMuted)
        let callTransaction = CXTransaction()
        callTransaction.addAction(muteAction)
        self.requestCall(callTransaction, action: "muteCall")
    }
    
    func holdCall(call: Call, onHold: Bool) {
        let muteAction = CXSetHeldCallAction(call: call.uuid, onHold: onHold)
        let callTransaction = CXTransaction()
        callTransaction.addAction(muteAction)
        self.requestCall(callTransaction, action: "holdCall") { [weak self] success in
            NSLog("VOIP-CALL: nativeHold=requestResult success=%@ held=%@",
                  String(success), String(onHold))
            DispatchQueue.main.async {
                self?.systemStateDidChange?()
            }
        }
    }
    
    func endCall(call: Call) {
        let endCallAction = CXEndCallAction(call: call.uuid)
        let callTransaction = CXTransaction()
        callTransaction.addAction(endCallAction)
        //requestCall
        self.requestCall(callTransaction, action: "endCall")
    }

    func answerCall(call: Call, completion: @escaping (Bool) -> Void) {
        requestCall(CXTransaction(action: CXAnswerCallAction(call: call.uuid)),
                    action: "answerCall", completion: completion)
    }
    
    func connectedCall(call: Call) {
        guard let callItem = self.callWithUUID(uuid: call.uuid) else {
            return
        }
        callItem.connectedCall(completion: nil)

        if callItem.isOutGoing || callItem.data.isAccepted {
            return
        }
        
        let answerAction = CXAnswerCallAction(call: call.uuid)        
        let transaction = CXTransaction(action: answerAction)

        callController.request(transaction) { error in
            if let error = error {
                print("Error answering call: \(error.localizedDescription)")
            } else {
                // Call successfully answered
            }
        }
    }
    
    func endCallAlls() {
        let calls = callController.callObserver.calls
        for call in calls {
            let endCallAction = CXEndCallAction(call: call.uuid)
            let callTransaction = CXTransaction()
            callTransaction.addAction(endCallAction)
            self.requestCall(callTransaction, action: "endCallAlls")
        }
    }
    
    func activeCalls() -> [[String: Any]] {
        let calls = callController.callObserver.calls
        var json = [[String: Any]]()
        for call in calls {
            let callItem = self.callWithUUID(uuid: call.uuid)
            if(callItem != nil){
                var item: [String: Any] = callItem!.data.toJSON()
                item["accepted"] = callItem?.hasConnected
                // Answer can be acknowledged before SIP connects. Keep this
                // recoverable even after its CXAction leaves waitingActions.
                item["answerPending"] = callItem!.data.isAccepted && !callItem!.hasConnected
                json.append(item)
            }else {
                let item: [String: String] = ["id": call.uuid.uuidString]
                json.append(item)
            }
        }
        return json
    }
    
    
    func setHold(call: Call, onHold: Bool) {
        let handleCall = CXSetHeldCallAction(call: call.uuid, onHold: onHold)
        let callTransaction = CXTransaction()
        callTransaction.addAction(handleCall)
        //requestCall
    }
    
    
    private func requestCall(_ transaction: CXTransaction, action: String, completion: ((Bool) -> Void)? = nil) {
        callController.request(transaction){ error in
            if let error = error {
                //fail
                print("Error requesting transaction: \(error)")
            }else {
                if(action == "startCall"){
                    //TODO: push notification for Start Call
                }else if(action == "endCall" || action == "endCallAlls"){
                    //TODO: push notification for End Call
                }
                print("Requested transaction successfully: \(action)")
            }
            completion?(error == nil)
        }
    }
    
    
    private func getHandleType(_ handleType: String?) -> CXHandle.HandleType {
        var typeDefault = CXHandle.HandleType.generic
        switch handleType {
        case "number":
            typeDefault = CXHandle.HandleType.phoneNumber
            break
        case "email":
            typeDefault = CXHandle.HandleType.emailAddress
        default:
            typeDefault = CXHandle.HandleType.generic
        }
        return typeDefault
    }
    
    
    static let callsChangedNotification = Notification.Name("CallsChangedNotification")
    var callsChangedHandler: (() -> Void)?
    
    func callWithUUID(uuid: UUID) -> Call?{
        guard let idx = calls.firstIndex(where: { $0.uuid == uuid }) else { return nil }
        return calls[idx]
    }
    
    func addCall(_ call: Call){
        calls.append(call)
        call.stateDidChange = { [weak self] in
            guard let strongSelf = self else { return }
            strongSelf.callsChangedHandler?()
            strongSelf.postCallNotification()
        }
        callsChangedHandler?()
        postCallNotification()
    }
    
    func removeCall(_ call: Call){
        guard let idx = calls.firstIndex(where: { $0 === call }) else { return }
        calls.remove(at: idx)
        callsChangedHandler?()
        postCallNotification()
    }
    
    func removeAllCalls() {
        calls.removeAll()
        callsChangedHandler?()
        postCallNotification()
    }
    
    private func postCallNotification(){
        NotificationCenter.default.post(name: type(of: self).callsChangedNotification, object: self)
    }
    
    
}
