#!/bin/bash

if [ $# -ne 3 ]; then
    echo "usage: index.sh memory_in_mb path/to/conf.txt metric">&2
    exit 1
fi

mb=$1
conf=$2
metric=$3

export MAVEN_OPTS="-Xmx${mb}M -ea -server"
mvn compile &&
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.sim.utils.SimilarityConsole" \
              -D exec.classpathScope=runtime  \
              -D exec.args="$conf $metric" 
