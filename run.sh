#!/usr/bin/env bash

./gradlew -q clean build

for step in step-0 step-1 step-2 step-3 step-4 step-5 step-6
do
  (
    cd $step
    echo "Running: $step"
    docker build -t java11-tips-and-tricks:$step . >> run.log
    docker run --rm java11-tips-and-tricks:$step
  )
done

docker images | grep java11-tips-and-tricks | sort