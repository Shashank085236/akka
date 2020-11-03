package com.lightbend.akka.sample;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Printer extends AbstractLoggingActor {

  public static final class Greeting {
    public final String message;

    public Greeting(String message) {
      this.message = message;
    }
  }


  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Greeting.class, greeting -> {
            log().info(greeting.message);
        })
        .build();
  }

  public static Props props() {
    return Props.create(Printer.class);
  }
}
