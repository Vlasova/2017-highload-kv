#!/bin/bash
MAX_RUNS="$1"
ACK="$2"
FROM="$3"
SUM_TIME=0
i=0
until [ $i -eq $MAX_RUNS ]
do
	TIME=`curl -o /dev/null -w %{time_total} -s -X GET http://localhost:8080/v0/entity?id=key$i&replicas=$ACK/$FROM`
	TIME="${TIME/,/.}"
	SUM_TIME=$(echo "scale=3;$SUM_TIME + $TIME" | bc)
	let "i=i + 1"
done
REQUESTS=$(echo "scale=1;$MAX_RUNS / $SUM_TIME" | bc)
SUM_TIME=$(echo "scale=1;$SUM_TIME * 1000" | bc)
TIME_AVERAGE=$(echo "scale=3;$SUM_TIME / $MAX_RUNS" | bc)
echo "Average delay: $TIME_AVERAGE ms"
echo "Requests/sec: $REQUESTS"
