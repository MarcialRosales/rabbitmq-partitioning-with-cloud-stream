package com.pivotal.partitioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Random;

@SpringBootApplication
@EnableBinding(MessagingBridge.class)
@EnableScheduling
public class TradeExecutorApplication {
	private Logger logger = LoggerFactory.getLogger(TradeExecutorApplication.class);

	private volatile int tradesCount;

	@Autowired
	private MessagingBridge messagingBridge;

	@StreamListener(MessagingBridge.TRADES)
	public void execute(@Header("account") long account, @Payload String trade) {
		logger.info(String.format("[%d] %s (account: %d) done", ++tradesCount, trade, account));
	}


	public static void main(String[] args) {
		SpringApplication.run(TradeExecutorApplication.class, args);
	}

}
