#!/bin/bash

if [ $# -ne 4 ]; then
    echo "usage: $0 input_dir output_path max-sims memory_in_mb" >&2
    exit 1
fi

in=$1
out=$2
maxsims=$3
mb=$4

export MAVEN_OPTS="-Xmx${mb}M -ea"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.sim.TextSimilarity" -D exec.classpathScope=runtime  -D exec.args="link $in $out $maxsims"
