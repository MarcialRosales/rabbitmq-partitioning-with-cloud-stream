package com.pivotal.partitioning;


import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

public interface MessagingBridge {

    @Output
    MessageChannel tradesEmitter();

    String TRADE_CONFIRMATION = "tradeConfirmations";

    @Input(TRADE_CONFIRMATION)
    SubscribableChannel tradeConfirmations();

}
