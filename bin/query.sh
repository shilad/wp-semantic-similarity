#!/bin/bash
#
#
# Example query 1: 'text:jazz ninlinks:[100 TO 5000000]'
# Example query 2: 'type:normal AND ninlinks:[100 TO 5000000]'
#


if [ $# -lt 2 ]; then
    echo "usage: $0 path/to/conf.txt index_name num_results <query.txt" >&2
    exit 1
fi

query=`cat`

echo "executing query $query"

export MAVEN_OPTS="-Xmx1000M -server -ea"
mvn compile &&
echo "$query" |
mvn exec:java -D exec.mainClass="edu.macalester.wpsemsim.lucene.WpQuery" -D exec.classpathScope=runtime  -D exec.args="$1 $2 $3"
