#!/bin/bash

if [ $# -ne 3 ]; then
    echo "usage: index.sh matrix_path lucene_path jvm_MB" >&2
    exit 1
fi

matrix=$1
lucene=$2
mb=$3

export MAVEN_OPTS="-Xmx${mb}M -ea -server"
mvn compile &&
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.matrix.MatrixSummarizer" -D exec.classpathScope=runtime  -D exec.args="$matrix $lucene"
