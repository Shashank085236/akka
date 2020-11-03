package com.lightbend.akka.IoT.devicegroupquery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.lightbend.akka.IoT.devicegroups.DeviceGroup;
import com.lightbend.akka.IoT.devices.Device;
import scala.concurrent.duration.FiniteDuration;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Terminated;

import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 *
 * Since we need a way to indicate how long we are willing to wait for responses,
 * it is time to use Akka feature, the built-in scheduler facility.
 *
 * We get the scheduler from the ActorSystem, which, in turn, is accessible from the actor’s context: getContext().getSystem().scheduler().
 * This needs an ExecutionContext which is the thread-pool that will execute the timer task itself.
 *
 * In our case, we use the same dispatcher as the actor by passing in getContext().dispatcher().
 *
 * The scheduler.scheduleOnce(time, actorRef, message, executor, sender)
 * method will schedule the message into the future by the specified time and send it to the actor actorRef.
 *
 * We need to create a message that represents the query timeout. We create a simple message CollectionTimeout without any parameters for this purpose.
 * The return value from scheduleOnce is a Cancellable which can be used to cancel the timer if the query finishes successfully in time.
 * At the start of the query, we need to ask each of the device actors for the current temperature.
 *
 * To be able to quickly detect devices that stopped before they got the ReadTemperature message we will also watch each of the actors.
 * This way, we get Terminated messages for those that stop during the lifetime of the query, so we don’t need to wait until the timeout to mark these as not available.
 *
 **/
public class DeviceGroupQuery extends AbstractActor {
    public static final class CollectionTimeout {
    }

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final Map<ActorRef, String> actorToDeviceId;
    final long requestId;
    final ActorRef requester;

    Cancellable queryTimeoutTimer;

    public DeviceGroupQuery(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
        this.actorToDeviceId = actorToDeviceId;
        this.requestId = requestId;
        this.requester = requester;

        queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(
                timeout, getSelf(), new CollectionTimeout(), getContext().dispatcher(), getSelf()
        );
    }

    public static Props props(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
        return Props.create(DeviceGroupQuery.class, actorToDeviceId, requestId, requester, timeout);
    }

    @Override
    public void preStart() {
        for (ActorRef deviceActor : actorToDeviceId.keySet()) {
            getContext().watch(deviceActor);
            deviceActor.tell(new Device.ReadTemperature(0L), getSelf());
        }
    }

    @Override
    public void postStop() {
        queryTimeoutTimer.cancel();
    }

    @Override
    public Receive createReceive() {
        return waitingForReplies(new HashMap<>(), actorToDeviceId.keySet());
    }

    public Receive waitingForReplies(
            Map<String, DeviceGroup.TemperatureReading> repliesSoFar,
            Set<ActorRef> stillWaiting) {
        return receiveBuilder()
                .match(Device.RespondTemperature.class, r -> {
                    ActorRef deviceActor = getSender();
                    DeviceGroup.TemperatureReading reading = r.getValue()
                            .map(v -> (DeviceGroup.TemperatureReading) new DeviceGroup.Temperature(v))
                            .orElse(new DeviceGroup.TemperatureNotAvailable());
                    receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar);
                })
                .match(Terminated.class, t -> {
                    receivedResponse(t.getActor(), new DeviceGroup.DeviceNotAvailable(), stillWaiting, repliesSoFar);
                })
                .match(CollectionTimeout.class, t -> {
                    Map<String, DeviceGroup.TemperatureReading> replies = new HashMap<>(repliesSoFar);
                    for (ActorRef deviceActor : stillWaiting) {
                        String deviceId = actorToDeviceId.get(deviceActor);
                        replies.put(deviceId, new DeviceGroup.DeviceTimedOut());
                    }
                    requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, replies), getSelf());
                    getContext().stop(getSelf());
                })
                .build();
    }

    public void receivedResponse(ActorRef deviceActor,
                                 DeviceGroup.TemperatureReading reading,
                                 Set<ActorRef> stillWaiting,
                                 Map<String, DeviceGroup.TemperatureReading> repliesSoFar) {
        getContext().unwatch(deviceActor);
        String deviceId = actorToDeviceId.get(deviceActor);

        Set<ActorRef> newStillWaiting = new HashSet<>(stillWaiting);
        newStillWaiting.remove(deviceActor);

        Map<String, DeviceGroup.TemperatureReading> newRepliesSoFar = new HashMap<>(repliesSoFar);
        newRepliesSoFar.put(deviceId, reading);
        if (newStillWaiting.isEmpty()) {
            requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar), getSelf());
            getContext().stop(getSelf());
        } else {
            getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
        }
    }
}