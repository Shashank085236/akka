package com.lightbend.akka.supervision;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;

public class NonTrustWorthyChild extends AbstractLoggingActor {

  private int count = 0;

  public static class IncrementCount {}

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(IncrementCount.class, this::incrementCount)
            .build();
  }

  @Override
  public void preStart() {
    log().info("NonTrustWorthyChild starting...");
  }

  @Override
  public void postStop() {
    log().info("NonTrustWorthyChild stopped...");
  }


  private void incrementCount(IncrementCount counter) {
    count++;
    if(count % 5 == 0) {
      throw new RuntimeException("count divisible by 5 error - "+count);
    }
    log().info("count - "+count);
  }

  public static Props props() {
    return Props.create(NonTrustWorthyChild.class);
  }

}
