package com.pivotal.partitioning;


import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

public interface MessagingBridge {

    String TRADES = "trades";

    @Input(TRADES)
    SubscribableChannel trades();

}
