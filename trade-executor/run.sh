#!/usr/bin/env bash

export SPRING_PROFILES_ACTIVE=cloud
export CF_INSTANCE_INDEX=${1:-"0"}
export INSTANCE_INDEX=${1:-"0"}
export VCAP_APPLICATION='{"application_name":"trade-executor"}'
export VCAP_SERVICES=$(cat src/main/resources/singleNode.json)

echo "Running trade-executor #$CF_INSTANCE_INDEX ..."
java $JAVA_ARGS -jar target/trade-executor-0.0.1-SNAPSHOT.jar
