#!/bin/bash

if [ $# -ne 2 ]; then
    echo "usage: index.sh path/to/conf.txt memory_in_mb" >&2
    exit 1
fi

conf=$1
mb=$2
cache_mb=$(($mb * 1 / 2))

export MAVEN_OPTS="-Xmx${mb}M -ea"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.lucene.AllIndexBuilder" -D exec.classpathScope=runtime  -D exec.args="$conf $cache_mb"
