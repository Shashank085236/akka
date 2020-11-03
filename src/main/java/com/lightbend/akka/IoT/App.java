package com.lightbend.akka.IoT;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import com.lightbend.akka.StdIn;

public class App {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create();
        final ActorRef deviceManagerActor = system.actorOf(DeviceManager.props(), "DeviceManager");
        deviceManagerActor.tell(new DeviceManager.RequestTrackDevice("G1", "D1"), ActorRef.noSender());
        deviceManagerActor.tell(new DeviceManager.RequestTrackDevice("G2", "D2"), ActorRef.noSender());
        deviceManagerActor.tell(new DeviceManager.RequestTrackDevice("G1", "D3"), ActorRef.noSender());
        deviceManagerActor.tell(new DeviceManager.RequestTrackDevice("G1", "D1"), ActorRef.noSender());
        //deviceManagerActor.tell(PoisonPill.getInstance(), ActorRef.noSender());

        System.out.println("ENTER to terminate");
        StdIn.readLine();
        system.terminate();
    }
}
