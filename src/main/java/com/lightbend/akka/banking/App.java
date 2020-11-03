package com.lightbend.akka.banking;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.lightbend.akka.StdIn;

import java.util.Random;

public class App {
    public static void main(String[] args) {
        final int CONCURRENT_TRANS_ATTEMPTS = 10;
        Random random = new Random();
        ActorSystem system = ActorSystem.create();
        final ActorRef transactionActor = system.actorOf(AccountTransactionActor.props(), "transactionActor");
        transactionActor.tell(new AccountTransactionActor.Deposit(100), ActorRef.noSender());

        for(int i = 0; i < CONCURRENT_TRANS_ATTEMPTS; i++) {
           new Thread(new Runnable() {
                @Override
                public void run() {
                    transactionActor.tell(new AccountTransactionActor.Withdraw(random.nextInt(50)+1), ActorRef.noSender());
                }
            }).start();

        }

        System.out.println("ENTER to terminate");
        StdIn.readLine();
        system.terminate();
    }
}
