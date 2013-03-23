#!/bin/bash

if [ $# -lt 3 ]; then
    echo "usage: $0 memory_in_mb path/to/conf.json output_path {metric1 metric2 ...}" >&2
    exit 1
fi

mb=$1
conf=$2
out=$3
shift 3
args=(${@// /\\ })

export MAVEN_OPTS="-Xmx${mb}M -ea -server"
mvn compile &&
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.sim.utils.SimilarityAnalyzer" -D exec.classpathScope=runtime  -D exec.args="$conf $out ${args[*]}"
