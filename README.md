# Data Partitioning with Spring Cloud Stream over RabbitMQ

- [Introduction](#introduction)
  - [Goal of this guide](#goal-of-this-guide)
  - [Why do we do need to partition the data?](#why-do-we-do-need-to-partition-the-data)
  - [Why do we need to use Spring Cloud Stream?](#why-do-we-need-to-use-spring-cloud-stream)
- [Getting started](#getting-started)
  - [Brief introduction to Spring Cloud Stream](#brief-introduction-to-spring-cloud-stream)
  - [How Channels are bound to RabbitMQ Resources](#how-channels-are-bound-to-rabbitmq-resources)
  - [Deep-dive on How Channels are bound to RabbitMQ Resources](#deep-dive-on-how-channels-are-bound-to-rabbitmq-resources)
- [Data Partitioning](#data-partitioning)
  - [Configuring Producers](#configuring-producers)
  - [Configuring Producers](#configuring-consumers)
  - [Scaling consumers](#Scaling-consumers)
- [Resiliency](#resiliency)
  - [Producer resiliency](#producer-resiliency)
  - [Consumer Resiliency](#consumer-resiliency)
- [Bootstrapping RabbitMQ ConnectionFactory](#bootstrapping-rabbitmq-connectionfactory)

# Introduction

## Goal of this guide

The goal is to explore what it takes to use [Spring Cloud Stream](https://docs.spring.io/spring-cloud-stream/) to partition messages in RabbitMQ. To do we have created 2 sample Spring Boot applications and a few simple steps on how to run them to see data partitioning in action.

## Why do we do need to partition the data?

There could be various reasons. Here are threes:
- **Ordered processing** - We need strictly ordered processing of messages but we cannot afford to have a single consumer. For this reason, we partition the messages based on the business criteria that imposes the ordering. For instance, we should process, in order, all the messages for a given account. It does not matter the order of messages between accounts.
- **Data Consistency** - In a time-windowed average calculation example, it is important that all measurements from any given sensor are processed by the same application instance.
- **Performance** - We need to increase the message throughput or reduce message latency. For that matter, we want messages to reside in multiple queues rather than just one queue. RabbitMQ uses an Erlang process per queue, this means processing messages for a queue is single threaded.

## Why do we need to use Spring Cloud Stream?

Spring Cloud Stream is a framework for building message-driven microservice applications. It has built-in support for data partition. Therefore, it sounded reasonable to use it.

Spring Cloud Stream implements Publish-Subscribe messaging pattern. It does not support Request-Reply messaging pattern although RabbitMQ does. It supports persistent (a.k.a. durable) publish-subcribe model and also non-persistent (a.k.a. non-durable) subscriptions.

In Spring Cloud Stream, each consumer creates its own queue (auto-delete, and exclusive). Spring Cloud Stream also has the concept of consumer group which allows multiple consumers to read from the same queue, in a [consumer competing pattern](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CompetingConsumers.html).

# Getting started

Prerequisites:
- Java 1.8
- Maven +3.3
- Docker installed unless you are running RabbitMQ elsewhere or even locally

```bash
git clone https://github.com/MarcialRosales/rabbitmq-partitioning-with-cloud-stream
cd rabbitmq-partitioning-with-cloud-stream
```

Under the root folder we have two Spring Boot applications:
- [Trade Requestor](trade-requestor) which will be responsible for sending *trade requests* on behalf of accounts -identified by a number which we randomly generate.
- [Trade Executor](trade-executor) which will be responsible for receiving those *trade requests* and simulate that it executes them by simply logging them to the standard output.

We want to partition the trades based on the account the trade is for. We want 2 partitions in total, hence the trades will be spread between these two partitions. This means that we will need at least 2 **trade executor** instances, one for each partition. We can have as many instances of **trade requestor** as we want.
```
    [               ]-------->[ partition 1]----->[trade executor #0]
    [trade requestor]
    [               ]-------->[ partition 2]----->[trade executor #1]
```

To run the trade requestor, we recommend launching a terminal and run this command from it:
```bash
cd trade-requestor
mvn install
java -jar target/trade-requestor-0.0.1-SNAPSHOT.jar
```

To run the trade executor instance 0, we recommend launching a separate terminal and run this command from it:
```bash
cd trade-executor
mvn install
java -jar target/trade-executor-0.0.1-SNAPSHOT.jar --spring.cloud.stream.instanceIndex=0
```

To run the trade executor instance 1, we recommend launching a separate terminal and run this command from it:
```bash
cd trade-executor
java -jar target/trade-executor-0.0.1-SNAPSHOT.jar --spring.cloud.stream.instanceIndex=1
```

## Brief introduction to Spring Cloud Stream

Stream Cloud Stream is based on the core concept of a *Channel*. A channel, or *pipe*, can be either an input or an output channel. Input channels are used to receive messages and output channels to send them. That's it.
![Spring Cloud Stream Application](https://docs.spring.io/spring-cloud-stream/docs/current/reference/htmlsingle/images/SCSt-with-binder.png)

The application is responsible for declaring what channels it needs and to provide them to the corresponding business component that needs them.

Let's look at one of the sample applications. The **trade requestor** declares that it needs an output channel where to send *trade requests* messages. It declares the channel, `tradesEmitter`, in an interface that will act as a bridge between the messaging middleware -RabbitMQ- and the application business logic:

```Java
public interface MessagingBridge {

    @Output
    MessageChannel tradesEmitter();

}
```

That is just an interface, we need to tell Spring Cloud Stream to provide an implementation of that interface via the `@EnableBinding` annotation as shown below:
```Java
@SpringBootApplication
@EnableBinding(MessagingBridge.class)
public class TradeRequestorApplication {

  @Autowired
	private MessagingBridge messagingBridge;

  ....

}
```

Once, we have the `messagingBridge` wired onto our application, as shown above, we can use it to send messages like shown below:
```Java
public class TradeRequestorApplication {

  @Scheduled(fixedRate = 5000)
  public void produceTradeRequest() {
    ...
    messagingBridge.tradesEmitter().send(
        MessageBuilder.withPayload(body).setHeader("account", account).build());
  }
```

We have not discussed yet how these channels map to queues and exchanges, how they are named, etc.

## How Channels are bound to RabbitMQ Resources

Spring cloud Stream uses the term `bind` to refer to mapping of the channels to the underlying messaging middleware.

A *Binder* is a messaging-middleware implementation. RabbitMQ is the implementation we use here. We declare multiple *binders* on a single application, if needed. Imagine our application needs to talk to 2 RabbitMQ clusters. Here we would need 2 binders.  

We declare the *binder* via configuration like shown below:
```yaml
spring:
  cloud:
    stream:
      binders:
        local_rabbit:
          type: rabbit
          environment:
            spring:
              rabbitmq:
                host: localhost
                port: 5672
                username: guest
                password: guest
                virtual-host: /
```
> Extracted from [trade-requestor/src/main/resources/application.yml](trade-requestor/src/main/resources/application.yml)

A *Binding* is the mapping of a channel to a destination in a configured *binder*. Below we see the *bindings* for the *Output* channel `tradesEmitter` ([MessagingBridge.tradesEmitter()](trade-requestor/src/main/java/com/pivotal/partitioning/MessagingBridge.java#L10)) which is mapped to the `local_rabbit` *binder* we declared above.
```yaml
spring:
  cloud:
    stream:
      bindings:
        tradesEmitter:
          destination: trades
          binder: local_rabbit
          producer:
            partitionCount: 2
            partitionKeyExpression: headers['account']
            partitionSelectorName: partitionSelectorStrategy  

```
> Also extracted from [trade-requestor/src/main/resources/application.yml](trade-requestor/src/main/resources/application.yml)

## Deep-dive on How Channels are bound to RabbitMQ Resources

By default, the RabbitMQ Binder implementation maps each destination to a *TopicExchange*.

For each consumer group, a Queue is bound to that TopicExchange. Each consumer instance has a corresponding RabbitMQ Consumer instance for its group’s Queue. For anonymous consumers, an auto-delete queue (with a randomized unique name) is used.

We know that a channel is a *pipe* abstraction used in Spring Cloud Stream. The name of the channel is what we define in the annotation `@Sink` or `@Source`,
for instance when we declared these
```Java
    @Input(TRADES)
    SubscribableChannel trades();

    @Input(TRADE_CONFIRMATION)
    SubscribableChannel tradeConfirmations();

```
A channel will be mapped to an exchange and ultimately to one queue -in case of specifying one consumer group- or
one queue per consumer/listener -in case of not using consumer groups.

Let's understand how exchanges and queues are named.

Let's start first how channels are named and we will use one of the channels we used in our application. The name of the channel we used for **trades**
is given by the constant `TRADES`. If we did not specify any name, it would have taken the name of the annotated method, in our case it would be `trades`.

The exchange is named after the channel name if we did not configure a `destination` for the channel in the `bindinds`
configuration entry. See below (extracted from `application_no_consumer_groups.yml`). The channel named `trades` has a destination called `q_trades` therefore the exchange
will be named `q_trades` of type *Topic*.

```yaml
spring:
  cloud:
    stream:
      bindings:
        trades:  # com.pivotal.partitioning.TRADES
          destination: q_trades
          binder: local_rabbit
          group: trades_group
        tradeConfirmations:  # com.pivotal.partitioning.TRADE_CONFIRMATION
          destination: q_trade_confirmations
          binder: local_rabbit

```

And queues are named differently depending whether we use *consumer groups* or not. If we do not use consumer groups, each listener will create
a queue named as follows `<channel_destination_name>.anonymous.<unique_id>` e.g. `q_trade_confirmations.anonymous.XbaJDGmDT7mNEgD6_ru9zw`.
If the channel is configured with *consumer groups*, then it will be named as follows `<channel_destination_name>>.<consumer_group_name>` e.g. `q_trades.trades_group`.
![queue channels](assets/queue_channels.png)

Consumer group subscriptions are durable. Anonymous subscriptions are non-durable by nature.
In general, it is preferable to always specify a consumer group when binding an application to a given destination. It only makes sense
to not use consumer groups when our consumers do not want to process messages that occur while they are not listening.

The producer will send a message with the routing key `<destination_name>-<partition_key>`, e.g. `q_trades-0`. And the queue `q_trades.trades_group-0` is bound to the
exchange `q_trades` with that routing key.

All this information is available via the actuator endpoint: `curl localhost:8080/actuator/bindings | jq .`

# Data Partitioning

We need to configure *partitioning* in the producer and in the consumer side. And in terms of Spring cloud Stream, that means configuring the output and input *bindings*.

Key configuration:
- **Producers** need to know how many partitions there will be so that they know how to split the data. [here](trade-requestor/src/main/resources/application.yml#L10) is how *trade requestor* does it.
- **Consumers** need to know which partition they will be reading from. Each consumer instance must be configured with an instance identifier. For instance, earlier on we ran the *trade executor* application with the argument `--spring.cloud.stream.instanceIndex=0`.

In the next two sections we discuss in greater detail what it takes to configure producers and consumers although the key configuration is what we just said above.

## Configuring Producers

We need to tell producers which is the partition key to used. A domain event usually has a partition key so that
it ends up in the same partition with related messages. This can be configured into ways:

- One way is to define an SpEL expression that when evaluated against the outgoing message it returns the value to partition. This expression is configured in the property  `spring.cloud.stream.bindings.<channel_name>.producer.partitionKeyExpression`
- Another way is to define our own custom logic and create an instance of that logic and expose it as a @Bean. We set the name of the @bean in `spring.cloud.stream.bindings.<channel_name>.producer.partitionKeyExtractorName`

We saw earlier that the key property we need to configure is `spring.cloud.stream.bindings.<channel_name>.producer.partitionCount`. The partition number must be between 0 and `partitionCount`- 1. This value is obtained using the formula:
 `key.hashCode() % partitionCount`. However, we can provide our own formula implementing `org.springframework.cloud.stream.binder.PartitionSelectorStrategy`
 similar to how we did it for partition key selection. And specify the @Bean name via the property `partitionSelectorName`.

## Configuring Consumers

We need to tell the consumer that it would be consuming from a partition.
`spring.cloud.stream.bindings.<channel_name>.consumer.partitioned: true`

## Scaling consumers

We can run as many instances of the **trade executor** application as needed provided they are configured with an instance identifier which is between 0 and `partitionCount - 1`. Where the `partitionCount` is defined in the **trade executor** application.

If we launched a **trade executor** with a instance id of `3` where there are only 2 partitions, that instance will be reading from a partition queue which will never get any messages.

**TL;DR** Once we have partition queues with data we cannot change the number of partitions and/or how partition strategy because we could end up with a data for partition in more than one partition queue.

**Deploying applications to Cloud Foundry**: TODO check if Spring Cloud Stream uses [CF_INSTANCE_INDEX](https://docs.cloudfoundry.org/devguide/deploy-apps/environment-variable.html#CF-INSTANCE-INDEX) to configure `spring.cloud.stream.instanceIndex` property. If it does then we need to override it unless we either do not use auto-scaling feature or never deploy more instances than partitions.

# Resiliency

## Producer resiliency

Spring Cloud Stream uses the Spring Retry library to facilitate successful message processing. See Retry Template for more details.

**spring.cloud.stream.bindings.<channel>.producer.requiredGroups** - A comma-separated list of groups to which the producer must ensure message delivery even if they start after it has been created (for example, by pre-creating durable queues in RabbitMQ).

If retry is enabled (maxAttempts > 1), failed messages are delivered to the DLQ after retries are exhausted. If retry is disabled (maxAttempts = 1), you should set requeueRejected to false (the default) so that failed messages are routed to the DLQ, instead of being re-queued. In addition, republishToDlq causes the binder to publish a failed message to the DLQ (instead of rejecting it). This feature lets additional information (such as the stack trace in the x-exception-stacktrace header) be added to the message in headers.
(https://docs.spring.io/spring-cloud-stream/docs/current/reference/htmlsingle/#_rabbitmq_binder_overview)

**spring.cloud.stream.bindings.<channel>.producer.deliveryMode** - Delivery mode is PERSISTENT by default.


## Consumer Resiliency


# Bootstrapping RabbitMQ ConnectionFactory

Credentials coming from Cloud Connectors will be preferred over credentials coming over local spring configuration (.ak.a. auto-configuration).
This can be changed with this setting `spring.cloud.stream.overrideCloudConnectors`
More details here https://docs.spring.io/spring-cloud-stream/docs/current/reference/htmlsingle/#_binding_service_properties

TODO expand applications so that they get the credentials from VCAP_SERVICES and deploy them in Cloud Foundry
