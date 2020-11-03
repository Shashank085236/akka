package com.lightbend.akka.IoT.devices;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import com.lightbend.akka.IoT.DeviceManager;

import java.util.Optional;

/**
 *  - Registers a device(sensor)
 *  - Records temperature when pushed by sensor
 *  - Allows reading temperature on demand
 */

public class Device extends AbstractLoggingActor {
    final String groupId;
    final String deviceId;
    Optional<Double> lastTemperatureReading = Optional.empty();

    public Device(String groupId, String deviceId) {
        this.groupId = groupId;
        this.deviceId = deviceId;
    }

    public static final class RecordTemperature {
        final long requestId;
        final double value;

        public RecordTemperature(long requestId, double value) {
            this.requestId = requestId;
            this.value = value;
        }
    }

    public static final class TemperatureRecorded {
        final long requestId;

        public TemperatureRecorded(long requestId) {
            this.requestId = requestId;
        }

        public long getRequestId(){return this.requestId;}
    }

    public static final class ReadTemperature {
        final long requestId;

        public ReadTemperature(long requestId) {
            this.requestId = requestId;
        }

        public long getRequestId() {
            return requestId;
        }
    }

    public static final class RespondTemperature {
        final long requestId;
        final Optional<Double> value;

        public RespondTemperature(long requestId, Optional<Double> value) {
            this.requestId = requestId;
            this.value = value;
        }

        public Optional<Double> getValue(){return this.value;}
    }


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DeviceManager.RequestTrackDevice.class, r -> {
                    if (this.groupId.equals(r.groupId) && this.deviceId.equals(r.deviceId)) {
                        log().info("Device trying to send DeviceRegistered ack for device {} to {}", deviceId, getSender());

                        getSender().tell(new DeviceManager.DeviceRegistered(deviceId), getSelf());
                    } else {
                        log().warning(
                                "Ignoring TrackDevice request for {}-{}.This actor is responsible for {}-{}.",
                                r.groupId, r.deviceId, this.groupId, this.deviceId
                        );
                    }
                })
                .match(RecordTemperature.class, r -> {
                    log().info("Recorded temperature reading {} with {}", r.value, r.requestId);
                    lastTemperatureReading = Optional.of(r.value);
                    getSender().tell(new TemperatureRecorded(r.requestId), getSelf());
                })
                .match(ReadTemperature.class, r -> {
                    getSender().tell(new RespondTemperature(r.requestId, lastTemperatureReading), getSelf());
                })
                .build();
    }

    @Override
    public void preStart() {
        log().info("Device actor {}-{} started", groupId, deviceId);
    }

    @Override
    public void postStop() {
        log().info("Device actor {}-{} stopped", groupId, deviceId);
    }

    public static Props props(String groupId, String deviceId) {
        return Props.create(Device.class, groupId, deviceId);
    }

}