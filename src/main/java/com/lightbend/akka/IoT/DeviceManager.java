package com.lightbend.akka.IoT;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import com.lightbend.akka.IoT.devicegroups.DeviceGroup;
import com.lightbend.akka.banking.AccountTransactionActor;

import java.util.HashMap;
import java.util.Map;

public class DeviceManager extends AbstractLoggingActor {

    final Map<String, ActorRef> groupIdToActor = new HashMap<>();
    final Map<ActorRef, String> actorToGroupId = new HashMap<>();

    public static final class RequestTrackDevice {
        public final String groupId;
        public final String deviceId;

        public RequestTrackDevice(String groupId, String deviceId) {
            this.groupId = groupId;
            this.deviceId = deviceId;
        }
    }

    public static final class DeviceRegistered {
        public final String deviceId;
        public DeviceRegistered(String deviceId){ this.deviceId = deviceId;}
    }

    public Receive createReceive() {
        return receiveBuilder()
                .match(RequestTrackDevice.class, this::onTrackDevice)
                .match(Terminated.class, this::onTerminated)
                .match(DeviceRegistered.class, (message) -> {log().info("DM - Device registered - {}", message.deviceId);})
                .build();
    }

    private void onTrackDevice(RequestTrackDevice trackMsg) {
        String groupId = trackMsg.groupId;
        ActorRef ref = groupIdToActor.get(groupId);
        if (ref != null) {
            ref.tell(trackMsg, getSelf());
        } else {
            log().info("Creating device group actor for {}", groupId);
            ActorRef groupActor = getContext().actorOf(DeviceGroup.props(groupId), "group-" + groupId);
            //watch actor for termination with Terminated message
            getContext().watch(groupActor);
            // forward message to groupActor for tracking
            groupActor.tell(trackMsg, getSelf());
            groupIdToActor.put(groupId, groupActor);
            actorToGroupId.put(groupActor, groupId);
        }
    }

    private void onTerminated(Terminated t) {
        ActorRef groupActor = t.getActor();
        String groupId = actorToGroupId.get(groupActor);
        log().info("Device group actor for {} has been terminated", groupId);
        actorToGroupId.remove(groupActor);
        groupIdToActor.remove(groupId);
    }

    @Override
    public void preStart() {
        log().info("DeviceManager started");
    }

    @Override
    public void postStop() {
        log().info("DeviceManager stopped");
    }

    public static Props props() {
        return Props.create(DeviceManager.class);
    }

}