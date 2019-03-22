package com.pivotal.partitioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binder.PartitionSelectorStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Random;

@SpringBootApplication
@EnableBinding(MessagingBridge.class)
@EnableScheduling
public class TradeRequestorApplication {
	private final Logger logger = LoggerFactory.getLogger(TradeRequestorApplication.class);

	private Random accountRandomizer = new Random(System.currentTimeMillis());

	@Autowired
	private MessagingBridge messagingBridge;

	@Autowired
	private PartitionSelectorStrategy partitionSelectorStrategy;

	@Scheduled(fixedRate = 5000)
	public void produceTradeRequest() {
		String body = String.format("Trade %d", System.currentTimeMillis());
		long account = accountRandomizer.nextInt(10);
		int partition = partitionSelectorStrategy.selectPartition(account, 2);

		logger.info("Producing trade request %s for account %d ... going to partition %d \n", body, account, partition);

		messagingBridge.tradesEmitter().send(
				MessageBuilder.withPayload(body).setHeader("account", account).build());
	}


	@Bean
	public PartitionSelectorStrategy partitionSelectorStrategy() {
		return (key, partitionCount) -> (int) (((Long)key).longValue() % partitionCount);
	}

	public static void main(String[] args) {
		SpringApplication.run(TradeRequestorApplication.class, args);
	}

}
