package com.lightbend.akka.IoT.devicegroups;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import com.lightbend.akka.IoT.DeviceManager;
import com.lightbend.akka.IoT.devicegroupquery.DeviceGroupQuery;
import com.lightbend.akka.IoT.devices.Device;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

//#query-added
public class DeviceGroup extends AbstractLoggingActor {

    final String groupId;
    final Map<String, ActorRef> deviceIdToActor = new HashMap<>();
    final Map<ActorRef, String> actorToDeviceId = new HashMap<>();

    public DeviceGroup(String groupId) {
        this.groupId = groupId;
    }


    public static final class RequestDeviceList {
        final long requestId;

        public RequestDeviceList(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class ReplyDeviceList {
        final long requestId;
        final Set<String> ids;

        public ReplyDeviceList(long requestId, Set<String> ids) {
            this.requestId = requestId;
            this.ids = ids;
        }
    }

    //#query-protocol
    public static final class RequestAllTemperatures {
        final long requestId;

        public RequestAllTemperatures(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class RespondAllTemperatures {
        final long requestId;
        final Map<String, TemperatureReading> temperatures;

        public RespondAllTemperatures(long requestId, Map<String, TemperatureReading> temperatures) {
            this.requestId = requestId;
            this.temperatures = temperatures;
        }

        public long getRequestId() {
            return this.requestId;
        }

        public Map<String, TemperatureReading> getTemperatures() {
            return temperatures;
        }
    }

    public static interface TemperatureReading {
    }

    public static final class Temperature implements TemperatureReading {
        public final double value;

        public Temperature(double value) {
            this.value = value;
        }
    }

    public static final class TemperatureNotAvailable implements TemperatureReading {
    }

    public static final class DeviceNotAvailable implements TemperatureReading {
    }

    public static final class DeviceTimedOut implements TemperatureReading {
    }
    //#query-protocol

    @Override
    public Receive createReceive() {
        //#query-added
        return receiveBuilder()
                .match(DeviceManager.RequestTrackDevice.class, this::onTrackDevice)
                .match(RequestDeviceList.class, this::onDeviceList)
                .match(Terminated.class, this::onTerminated)
                .match(RequestAllTemperatures.class, this::onAllTemperatures)
                .build();
    }
    //#query-added

    /**
     *
     * - forward the request so Device Actor can directly respond to original sender.
     * - After device Actor stops, the watcher receives a Terminated(actorRef) message which contains the reference to the watched actor.
     *   once stop, device actor must be removed from maps.
     */
    private void onTrackDevice(DeviceManager.RequestTrackDevice requestTrackDevice) {
        if (this.groupId.equals(requestTrackDevice.groupId)) {
            ActorRef ref = deviceIdToActor.get(requestTrackDevice.deviceId);
            if (ref != null) {
                ref.forward(requestTrackDevice, getContext());
            } else {
                log().info("Creating device actor for {}", requestTrackDevice.deviceId);
                ActorRef deviceActor = getContext().actorOf(Device.props(groupId, requestTrackDevice.deviceId), "device-" + requestTrackDevice.deviceId);
                getContext().watch(deviceActor);
                deviceActor.forward(requestTrackDevice, getContext());
                actorToDeviceId.put(deviceActor, requestTrackDevice.deviceId);
                deviceIdToActor.put(requestTrackDevice.deviceId, deviceActor);
            }
        } else {
            log().warning(
                    "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
                    groupId, this.groupId
            );
        }
    }

    private void onDeviceList(RequestDeviceList r) {
        getSender().tell(new ReplyDeviceList(r.requestId, deviceIdToActor.keySet()), getSelf());
    }

    private void onTerminated(Terminated t) {
        ActorRef deviceActor = t.getActor();
        String deviceId = actorToDeviceId.get(deviceActor);
        log().info("Device actor for {} has been terminated", deviceId);
        actorToDeviceId.remove(deviceActor);
        deviceIdToActor.remove(deviceId);
    }
    //#query-added

    private void onAllTemperatures(RequestAllTemperatures r) {
        // since Java collections are mutable, we want to avoid sharing them between actors (since multiple Actors (threads)
        // modifying the same mutable data-structure is not safe), and perform a defensive copy of the mutable map:
        //
        // Feel free to use your favourite immutable data-structures library with Akka in Java applications!
        Map<ActorRef, String> actorToDeviceIdCopy = new HashMap<>(this.actorToDeviceId);

        getContext().actorOf(DeviceGroupQuery.props(
                actorToDeviceIdCopy, r.requestId, getSender(), new FiniteDuration(3, TimeUnit.SECONDS)));
    }


    @Override
    public void preStart() {
        log().info("DeviceGroup {} started", groupId);
    }

    @Override
    public void postStop() {
        log().info("DeviceGroup {} stopped", groupId);
    }

    public static Props props(String groupId) {
        return Props.create(DeviceGroup.class, groupId);
    }
}