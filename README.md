

@EnableBinding(Process.class)
The annotation @EnableBinding configures the application to bind the channels INPUT and OUTPUT defined within the interface Processor.

Bindings — a collection of interfaces that identify the input and output channels declaratively
Binder — messaging-middleware implementation such as Kafka or RabbitMQ
Channel — represents the communication pipe between messaging-middleware and the application

Messages designated to destinations are delivered by the Publish-Subscribe messaging pattern.
Publishers categorize messages into topics, each identified by a name. Subscribers express interest in one or more topics.

Now, the subscribers could be grouped. A consumer group is a set of competing consumers, identified by a group id. All consumers read from the same
topic in a load-balanced manner.
When running multiple instances of our application, every time there is a new message in an input channel, all subscribers will be notified
 (unless they are part of a consumer group).



When multiple applications are running, it’s important to ensure the data is split properly across consumers. To do so, Spring Cloud Stream provides two properties:

spring.cloud.stream.instanceCount — number of running applications. The number of deployed instances of an application.
 Must be set for partitioning on the producer side. Must be set on the consumer side when using RabbitMQ .

spring.cloud.stream.instanceIndex — index of the current application. Used for partitioning with RabbitMQ



# Partition handling

We need to configure partitioning in the producer and in the consumer side. And in terms of Spring cloud Stream, that means
configuring the output and input bindings.

## Configuring Producers
We need to tell producers which is the partition key to used. A domain event usually has a partition key so that
it ends up in the same partition with related messages. This can be configured using two properties:

`spring.cloud.stream.bindings.<channel_name>.producer.partitionKeyExpression` — the expression to partition the payloads.
It is a SpEL expression that is evaluated against the outbound message for extracting the partitioning key.
or
`spring.cloud.stream.bindings.<channel_name>.producer.partitionKeyExtractorName` if we want to use a custom logic to determine the partition key.
The value is the @Bean's name which implements `org.springframework.cloud.stream.binder.PartitionKeyExtractorStrategy`.
and
`spring.cloud.stream.bindings.<channel_name>.producer.partitionCount` — Once we have determined the partition key, the partition selection
process determines the target partition based on a value between 0 and `partitionCount`- 1. This value is obtained using the formula:
 `key.hashCode() % partitionCount`. However, we can provide our own formula implementing `org.springframework.cloud.stream.binder.PartitionSelectorStrategy`
 similar to how we did it for partition key selection. And specify the @Bean name via the property `partitionSelectorName`.

## Configuring Consumers
We need to tell the consumer that it would be consuming from a partition.
`spring.cloud.stream.bindings.<channel_name>.consumer.partitioned: true`



# Resiliency

Spring Cloud Stream uses the Spring Retry library to facilitate successful message processing. See Retry Template for more details.


# Bootstrapping

Credentials coming from Cloud Connectors will be preferred over credentials coming over local spring configuration (.ak.a. auto-configuration).
This can be changed with this setting `spring.cloud.stream.overrideCloudConnectors`
More details here https://docs.spring.io/spring-cloud-stream/docs/current/reference/htmlsingle/#_binding_service_properties


# Channel configuration and how it maps to a physical destination in RabbitMQ

We know that a channel is a *pipe* abstraction used in Spring Cloud Stream. The name of the channel is what we define in the annotation `@Sink` or `@Source`,
for instance when we declared these
```
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

```
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


## Consumer Concurrency

```
consumer:
  concurrency: 2
```

