#!/bin/bash

if [ $# -ne 3 ]; then
    echo "usage: index.sh input_dir output_dir memory_in_mb" >&2
    exit 1
fi

in=$1
out=$2
mb=$3
cache_mb=$(($mb * 3 / 4))

export MAVEN_OPTS="-Xmx${mb}M -ea"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.IndexBuilder" -D exec.classpathScope=runtime  -D exec.args="$in $out $cache_mb"
