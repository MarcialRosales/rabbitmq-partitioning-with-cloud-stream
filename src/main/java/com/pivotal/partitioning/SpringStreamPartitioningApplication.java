package com.pivotal.partitioning;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.binder.PartitionKeyExtractorStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Random;

@SpringBootApplication
@EnableBinding(MessagingBridge.class)
@EnableScheduling
public class SpringStreamPartitioningApplication {

	private volatile int tradesCount;
	private Random accountRandomizer = new Random(System.currentTimeMillis());

	@Autowired
	private MessagingBridge messagingBridge;

	@Scheduled(fixedRate = 5000)
	public void produceTradeRequest() {
		String body = String.format("Trade %d", System.currentTimeMillis());
		long account = accountRandomizer.nextInt(10);
		System.out.printf("Producing trade request %s for account %d ... \n", body, account);
		messagingBridge.tradesEmitter().send(
				MessageBuilder.withPayload(body).setHeader("account", account).build());
	}

	@StreamListener(MessagingBridge.TRADES)
	public void tradeExecuter(@Header("account") long account, @Payload String trade) {
		messagingBridge.tradeConfirmationEmitter().send
				(MessageBuilder.withPayload(
						String.format("[%d] %s (account: %d) done", ++tradesCount, trade, account)).build());
	}


	@StreamListener(MessagingBridge.TRADE_CONFIRMATION)
	public void tradeConfirmations(String trade) {
		System.out.printf("[%s]\n", trade);
	}


	public static void main(String[] args) {
		SpringApplication.run(SpringStreamPartitioningApplication.class, args);
	}

}
