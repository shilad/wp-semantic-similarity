#!/bin/bash

if [ $# -ne 5 ]; then
    echo "usage: TSVMatrixWriter.sh conf gold out metric jvm_MB" >&2
    exit 1
fi

conf=$1
gold=$2
out=$3
metric=$4
mb=$5

export MAVEN_OPTS="-Xmx${mb}M -ea -server"
mvn compile &&
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.matrix.TSVMatrixWriter" -D exec.classpathScope=runtime  -D exec.args="$conf $gold $out $metric"
