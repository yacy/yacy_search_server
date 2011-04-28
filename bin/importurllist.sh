#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/Crawler_p.html?bookmarkFolder=/crawlStart&crawlingDomMaxPages=10000&intention=&range=domain&indexMedia=on&recrawl=nodoubles&storeHTCache=on&sitemapURL=&repeat_time=7&crawlingQ=on&crawlingIfOlderUnit=day&cachePolicy=ifexist&indexText=on&crawlingMode=file&crawlingURL=http://&bookmarkTitle=&mustnotmatch=&crawlingstart=import&mustmatch=.*&crawlingIfOlderNumber=7&repeat_unit=seldays&crawlingDepth=0&crawlingFile=$1" > /dev/null
