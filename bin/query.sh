#!/bin/bash
#
#
# Example query 1: 'text:jazz ninlinks:[100 TO 5000000]'
# Example query 2: 'type:normal AND ninlinks:[100 TO 5000000]'
#


if [ $# -lt 4 ]; then
    echo "usage: $0 path/to/conf.txt index_name num_results query....." >&2
    exit 1
fi

conf=$1
index=$2
num_results=$3

shift 3

query=$@

echo "executing query $query"

export MAVEN_OPTS="-Xmx1000M -server -ea"
mvn compile &&
echo "$query" |
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.lucene.WpQuery" -D exec.classpathScope=runtime  -D exec.args="$conf $index $num_results"
