#!/bin/bash

if [ $# -lt 4 ]; then
    echo "usage: $0 path/to/conf.json num_results path/to/model.out memory_in_mb sim1 sim2 ..." >&2
    exit 1
fi

conf=$1
num_results=$2
out=$3
mb=$4
shift 4
gold=dat/gold/combined.txt

export MAVEN_OPTS="-Xmx${mb}M -ea -server"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.sim.ensemble.EnsembleSimilarity" -D exec.classpathScope=runtime  -D exec.args="$conf $gold $out $num_results $@"
