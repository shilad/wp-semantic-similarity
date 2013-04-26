#!/bin/bash

if [ $# -lt 1 ]; then
    echo "usage: $0 {encode|decode|build} ...." >&2
    exit 1
fi
action=$1


if [ "$action"  == "build" ]; then
    if [ $# -ne 4 ]; then
        echo "usage: $0 build input.vw output_dir rank" >&2
        exit 1
    fi
    VW=./bin/vowpal_wabbit/vowpalwabbit/vw 
    if ! [[ -x $VW ]]; then
        echo "vowpal binary expected at $VW not found." >&2
        exit 1
    fi
    in=$2
    out=$3
    rank=$4
    rm -rf $out
    mkdir -p $out &&
    $VW --lda $rank \
        --lda_alpha 0.1 \
        --lda_rho 0.1 \
        --lda_D 4000000 \
         --minibatch 10000 \
        -b 22 \
        --power_t 0.5 \
        --initial_t 1 \
        --cache_file $out/vw.cache \
        --passes 3 \
        -p $out/articles.dat \
        --readable_model $out/topics.dat \
        -d dat/ensemble-sims.vw
else
    if [ $# -ne 4 ]; then
        echo "usage: $0 {encode|decode} input output jvm_mbs" >&2
        exit 1
    fi

    in=$2
    out=$3
    jvm_mbs=$4
    
    export MAVEN_OPTS="-Xmx${jvm_mbs}M -ea"
    mvn compile &&
    mvn exec:java \
        -D exec.mainClass="edu.macalester.wpsemsim.topics.VowpalTranslator" \
        -D exec.classpathScope=runtime  \
        -D exec.args="$action $in $out"
fi
