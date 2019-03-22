package com.pivotal.partitioning;


import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;

public interface MessagingBridge {

    @Output
    MessageChannel tradesEmitter();

}
