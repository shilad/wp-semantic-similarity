#!/bin/bash

if [ $# -ne 4 ]; then
    echo "usage: $0 jvm_MBs matrix_path output_dir rank" >&2
    exit 1
fi

mb=$1
matrix=$2
output=$3
rank=$4

export MAVEN_OPTS="-Xmx${mb}M -ea -server"
mvn compile &&
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.topics.FunkSVD" -D exec.classpathScope=runtime  -D exec.args="$matrix $output $rank"
