#!/bin/sh

if [ $# -ne 2 ]; then
    echo "usage: $0 path/to/conf.txt min_links" >&2
    exit 1
fi
conf=$1
minlinks=$2

./bin/query.sh $conf main 10000000 type:normal AND ninlinks:[$minlinks TO 1000000] | 
grep -v '\[INFO\]' |
egrep '^[0-9][0-9]*\.[0-9][0-9]*' |
egrep -v '^[^	]*	[^	]*	[0-9]{4}' |			# remove articles that start with a YEAR
egrep -v '^[^	]*	[^	]*	(January|February|March|April|May|June|July|August|September|October|November|December)'  | # articles that start with a month 
egrep -v '^[^	]*	[^	]*	[0-9][0-9]*$' |			# remove articles that are only digits
cat >dat/valid_ids.txt
