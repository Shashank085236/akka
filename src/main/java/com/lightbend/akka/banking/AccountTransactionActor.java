package com.lightbend.akka.banking;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;

public class AccountTransactionActor extends AbstractLoggingActor {

    // mutable internal state
    private int balance = 0;

    // immutable messages
    public static final class Deposit {
        private final int amount;
        public Deposit(int amount) { this.amount = amount; }
    }

    public static final class Withdraw {
        private final int amount;
        public Withdraw(int amount) { this.amount = amount; }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Deposit.class, this::onDeposit)
                .match(Withdraw.class, this::onWithdraw)
                .matchAny(any -> log().warning("Unidentified message ignored"))
                .build();
    }

    private void onDeposit(Deposit deposit) {
        log().info("Adding {} to account.", deposit.amount);
        balance = balance + deposit.amount;
        log().info("Your account balance - {}", balance);
    }

    private void onWithdraw(Withdraw withdraw) {
        log().info("Withdrawing {} from account.", withdraw.amount);
        if(balance >= withdraw.amount){
            balance = balance - withdraw.amount;
            log().info("Available balance - {}", balance);
        } else {
            log().warning("Insufficient fund in your account.");
        }
    }

    //Props is a configuration class to specify options for the creation of actors.
    public static Props props() {
        return Props.create(AccountTransactionActor.class);
    }
}
