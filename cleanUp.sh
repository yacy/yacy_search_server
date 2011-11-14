#!/bin/sh
JAVA_ARGS="-Xmx1024m -cp lib/yacycore.jar:lib/jcifs-1.3.15.jar:lib/httpcore-4.0.1.jar de.anomic.data.URLAnalysis"
nice -n 19 java $JAVA_ARGS -incell DATA/INDEX/freeworld/SEGMENTS/default used.dump
nice -n 19 java $JAVA_ARGS -diffurlcol DATA/INDEX/freeworld/SEGMENTS/default used.dump diffurlcol.dump
rm used.dump
nice -n 19 java $JAVA_ARGS -copy DATA/INDEX/freeworld/SEGMENTS/default diffurlcol.dump
rm diffurlcol.dump
