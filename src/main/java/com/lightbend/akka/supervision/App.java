/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.akka.supervision;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.lightbend.akka.StdIn;

public class App {

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create();

    final int STATE_RESTART = 5;
    final int STATE_RESUME = 0;
    final int STATE_ESCALATE = 20;
    final int STATE_STOP = -1;

    final ActorRef supervisor = system.actorOf(Supervisor.props(), "supervisor");
    for(int i = 0; i <= 10; i++) {
      supervisor.tell(new NonTrustWorthyChild.IncrementCount(), ActorRef.noSender());
    }
    System.out.println("ENTER to terminate");
    StdIn.readLine();
    system.terminate();
  }
}
