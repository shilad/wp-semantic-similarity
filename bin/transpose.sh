#!/bin/bash

if [ $# -ne 4 ]; then
    echo "usage: transpose.sh input_file output_file total_memory_in_mb buffer_in_mb" >&2
    exit 1
fi

in=$1
out=$2
memory_mb=$3
buffer_mb=$4

export MAVEN_OPTS="-Xmx${memory_mb}M -ea"
mvn compile
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.matrix.SparseMatrixTransposer" -D exec.classpathScope=runtime  -D exec.args="$in $out $buffer_mb"
