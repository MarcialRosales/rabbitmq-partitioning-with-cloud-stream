#!/usr/bin/env bash

export VCAP_APPLICATION='{"application_name":"trade-requestor"}'
export VCAP_SERVICES=$(cat src/main/resources/singleNode.json)
export CF_INSTANCE_INDEX=${1:-"0"}
export INSTANCE_INDEX=${1:-"0"}

echo "Running trade-requestor #$CF_INSTANCE_INDEX ..."
java $JAVA_ARGS -jar target/trade-requestor-0.0.1-SNAPSHOT.jar
