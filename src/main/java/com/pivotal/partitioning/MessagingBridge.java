package com.pivotal.partitioning;


import org.springframework.cloud.stream.annotation.Input;
import org.springframework.messaging.SubscribableChannel;

public interface MessagingBridge {

    String TRADES = "trades";
    String TRADE_CONFIRMATION = "tradeConfirmations";

    @Input(TRADES)
    SubscribableChannel trades();

    @Input(TRADE_CONFIRMATION)
    SubscribableChannel tradeConfirmations();

}
