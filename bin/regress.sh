#!/bin/bash

if [ $# -ne 2 ]; then
    echo "usage: $0 path/to/conf.json memory_in_mb" >&2
    exit 1
fi

conf=$1
mb=$2
gold=dat/gold/combined.tab.txt
out=/dev/null

export MAVEN_OPTS="-Xmx${mb}M -ea -server"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.sim.utils.SimilarityAnalyzer" -D exec.classpathScope=runtime  -D exec.args="$conf $gold $out"
