package com.lightbend.akka.supervision;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

import static akka.actor.SupervisorStrategy.escalate;
import static akka.actor.SupervisorStrategy.restart;
import static akka.actor.SupervisorStrategy.resume;
import static akka.actor.SupervisorStrategy.stop;

public class Supervisor extends AbstractLoggingActor {

  /*Supervision strategy
   * - AllForOneStrategy: decides for all actors
   * - OneForOneStrategy: decides for only one failing actor
   *
   * In this case if there are 2 retries within 10 seconds,
   * the Supervision action is taken on NonTrustWorthyChild actor.
   * In case actor is stopped, and all remaining messages are sent to the dead letter box (actor /deadLetters)
   */

  public static final OneForOneStrategy STRATEGY = new OneForOneStrategy(
          2,
          Duration.create(10, TimeUnit.SECONDS),
          DeciderBuilder
                  .match(RuntimeException.class, ex -> escalate())
/*                  .match(IllegalStateException.class, ex -> stop())
                  .match(ArithmeticException.class, ex -> resume())
                  .match(Exception.class, ex -> restart())*/
                  .build()
  );

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return STRATEGY;
  }

  @Override
  public Receive createReceive() {
    // hierarchy: /user/supervision/child
    final ActorRef child = getContext().actorOf(NonTrustWorthyChild.props(), "child");
    // forwards the message to child actor(Here forward is used to keep sender original)
    return receiveBuilder().matchAny(any -> child.forward(any, getContext())).build();
  }

  public static Props props() {
    return Props.create(Supervisor.class);
  }
}
