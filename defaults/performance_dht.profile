###
### YaCy Init File
###

# performance-settings
# delay-times for permanent loops (milliseconds)
# the idlesleep is the pause that an proces sleeps if the last call to the
# process job was without execution of anything;
# the busysleep is the pause after a full job execution
# the prereq-value is a memory pre-requisite: that much bytes must
# be available/free in the heap; othervise the loop is not executed
# and another idlesleep is performed
20_dhtdistribution_idlesleep=5000
20_dhtdistribution_busysleep=2000
20_dhtdistribution_memprereq=6291456
50_localcrawl_idlesleep=4000
50_localcrawl_busysleep=500
50_localcrawl_memprereq=4194304
50_localcrawl_isPaused=false
60_remotecrawlloader_idlesleep=60000
60_remotecrawlloader_busysleep=40000
60_remotecrawlloader_memprereq=2097152
60_remotecrawlloader_isPaused=false
62_remotetriggeredcrawl_idlesleep=10000
62_remotetriggeredcrawl_busysleep=1000
62_remotetriggeredcrawl_memprereq=6291456
62_remotetriggeredcrawl_isPaused=false
80_indexing_idlesleep=1000
80_indexing_busysleep=100
80_indexing_memprereq=6291456
82_crawlstack_idlesleep=5000
82_crawlstack_busysleep=1
82_crawlstack_memprereq=1048576

