package com.pivotal.partitioning;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableBinding(MessagingBridge.class)
@EnableScheduling
public class SpringStreamPartitioningApplication {

	private volatile int tradesCount;

	@Autowired
	@Qualifier(MessagingBridge.TRADES)
	private MessageChannel tradesEmitter;

	@Scheduled(fixedRate = 5000)
	public void produceTradeRequest() {
		String body = String.format("Trade %d", System.currentTimeMillis());
		System.out.println("Producing trade request ... " + body);
		tradesEmitter.send(MessageBuilder.withPayload(body).build());
	}

	@StreamListener(MessagingBridge.TRADES)
	@SendTo(MessagingBridge.TRADE_CONFIRMATION)
	public String tradeExecuter(String trade) {
		return String.format("[%d] %s done", ++tradesCount, trade);
	}


	@StreamListener(MessagingBridge.TRADE_CONFIRMATION)
	public void tradeConfirmations(String trade) {
		System.out.printf("[%s]\n", trade);
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringStreamPartitioningApplication.class, args);
	}

}
