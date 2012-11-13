#!/bin/bash

if [ $# -ne 3 ]; then
    echo "usage: $0 path/to/dictionary.txt.bz2 output_dir memory_in_mb" >&2
    exit 1
fi

in=$1
out=$2
mb=$3

export MAVEN_OPTS="-Xmx${mb}M -ea -server"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.concepts.DictionaryIndexer" -D exec.classpathScope=runtime  -D exec.args="$in $out 3 0.01"
