
package com.lightbend.akka.behaviour;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.lightbend.akka.StdIn;

/**
 * actor that changes behavior and becomes enabled/disabled based on messages
 */
public class App {

  static class Alarm extends AbstractActor {

    private final String password;

    //behavior
    private final Receive enabled;
    private final Receive disabled;


    // messages
    static class Activity {}

    static class Disable {
      private final String password;
      public Disable(String password) {
        this.password = password;
      }
    }

    static class Enable {
      private final String password;
      public Enable(String password) {
        this.password = password;
      }
    }


    public Alarm(String password) {
      this.password = password;
      enabled = receiveBuilder()
              .match(Activity.class, this::onActivity)
              .match(Disable.class, this::onDisable)
              .build();
      disabled = receiveBuilder()
              .match(Enable.class, this::onEnable)
              .build();
      getContext().become(disabled);
    }

    @Override
    public Receive createReceive() {
       return null;
    }

    private void onEnable(Enable enable) {
      if (password.equals(enable.password)) {
        System.out.println("Alarm enabled!");
        getContext().become(enabled);
      } else {
        System.out.println("Enabling behaviour failed - incorrect password.");
      }
    }

    private void onDisable(Disable disable) {
      if (password.equals(disable.password)) {
        System.out.println("Alarm disabled!");
        getContext().become(disabled);
      } else {
        System.out.println("Disabling behaviour failed - incorrect password.");
      }
    }

    private void onActivity(Activity activity) {
      System.out.println("behaviour behaviour!!!");
    }


    public static Props props(String password) {
      return Props.create(Alarm.class, password);
    }
  }

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create();

    final ActorRef alarm = system.actorOf(Alarm.props("skyline"), "behaviour");

    alarm.tell(new Alarm.Activity(), ActorRef.noSender());
    alarm.tell(new Alarm.Enable("wrongPassword"), ActorRef.noSender());
    alarm.tell(new Alarm.Enable("skyline"), ActorRef.noSender());

    // Test behaviour post enable
    alarm.tell(new Alarm.Activity(), ActorRef.noSender());

    alarm.tell(new Alarm.Disable("wrongPassword"), ActorRef.noSender());
    alarm.tell(new Alarm.Disable("skyline"), ActorRef.noSender());

    // Test behaviour post disable
    alarm.tell(new Alarm.Activity(), ActorRef.noSender());

    System.out.println("ENTER to terminate");
    StdIn.readLine();
    system.terminate();
  }
}
