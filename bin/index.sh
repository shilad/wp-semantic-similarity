#!/bin/bash

if [ $# -lt 2 ]; then
    echo "usage: index.sh path/to/conf.txt memory_in_mb [index_name1] [index_name2] ..." >&2
    exit 1
fi

conf=$1
mb=$2
cache_mb=$(($mb * 1 / 2))
if [ $cache_mb -gt 2000 ]; then
    cache_mb=2000
fi

shift 2

keys=$@

export MAVEN_OPTS="-Xmx${mb}M -ea"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.lucene.AllIndexBuilder" -D exec.classpathScope=runtime  -D exec.args="$conf $cache_mb $keys"
