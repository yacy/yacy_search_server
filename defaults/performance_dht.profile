###
### YaCy Init File
###

# performance-settings
# delay-times for permanent loops (milliseconds)
# the idlesleep is the pause that an process sleeps if the last call to the
# process job was without execution of anything;
# the busysleep is the pause after a full job execution
# the prereq-value is a memory pre-requisite: that much bytes must
# be available/free in the heap; othervise the loop is not executed
# and another idlesleep is performed

20_dhtdistribution_idlesleep=5000
20_dhtdistribution_busysleep=500
20_dhtdistribution_memprereq=12582912
50_localcrawl_idlesleep=2000
50_localcrawl_busysleep=60
50_localcrawl_memprereq=12582912
50_localcrawl_isPaused=false
60_remotecrawlloader_idlesleep=60000
60_remotecrawlloader_busysleep=10000
60_remotecrawlloader_memprereq=12582912
60_remotecrawlloader_isPaused=false
62_remotetriggeredcrawl_idlesleep=10000
62_remotetriggeredcrawl_busysleep=2000
62_remotetriggeredcrawl_memprereq=12582912
62_remotetriggeredcrawl_isPaused=false
80_indexing_idlesleep=1000
80_indexing_busysleep=0
80_indexing_memprereq=12582912

