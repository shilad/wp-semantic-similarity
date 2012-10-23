#!/bin/bash

if [ $# -ne 5 ]; then
    echo "usage: stage2sim.sh matrix_file matrix_transpose_file output_file max_results_per_doc total_memory_in_mb" >&2
    exit 1
fi

in=$1
trans=$2
out=$3
max_sims=$4
mb=$5

export MAVEN_OPTS="-Xmx${mb}M -ea"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.sim.PairwiseCosineSimilarity" -D exec.classpathScope=runtime  -D exec.args="$in $trans $out $max_sims"
