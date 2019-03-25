#!/usr/bin/env bash

# spring variable
export SPRING_PROFILES_ACTIVE=cloud

# CF variable
export CF_INSTANCE_INDEX=${1:-"0"}
export INSTANCE_INDEX=${1:-"0"}
export VCAP_APPLICATION='{"application_name":"trade-executor"}'
export VCAP_SERVICES=$(cat src/main/resources/singleNode.json)

# application variable
export PARTITION_COUNT=2
export TRADES_INSTANCE_INSTANCE=$((INSTANCE_INDEX % PARTITION_COUNT))

echo "Running trade-executor with ..."
echo "  - INSTANCE_INDEX= $INSTANCE_INDEX"
echo "  - CF_INSTANCE_INDEX = $CF_INSTANCE_INDEX"
echo "  - PARTITION_COUNT = $PARTITION_COUNT "
echo "  - TRADES_INSTANCE_INSTANCE instanceIndex = $((INSTANCE_INDEX % PARTITION_COUNT))"
java $JAVA_ARGS -jar target/trade-executor-0.0.1-SNAPSHOT.jar --spring.cloud.stream.bindings.trades.consumer.instanceIndex=$TRADES_INSTANCE_INSTANCE

