TMPFILE=`mktemp -t search` || exit 1
for address in `./up.sh $1`; do sleep 0.01; ./search1.sh -s $address $2 >> $TMPFILE & done
sleep 2
cat $TMPFILE
