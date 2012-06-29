port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
while [ 1 = 1 ]
do
curl "http://localhost:$port/NetworkPicture.png?width=768&height=576&bgcolor=FFFFFF" > /dev/null
done
