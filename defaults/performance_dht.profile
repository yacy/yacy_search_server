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
30_peerping_idlesleep=120000
30_peerping_busysleep=120000
30_peerping_memprereq=1048576
40_peerseedcycle_idlesleep=1800000
40_peerseedcycle_busysleep=1200000
40_peerseedcycle_memprereq=2097152
50_localcrawl_idlesleep=4000
50_localcrawl_busysleep=500
50_localcrawl_busysleep__pro=100
50_localcrawl_memprereq=4194304
50_localcrawl_isPaused=false
60_remotecrawlloader_idlesleep=60000
60_remotecrawlloader_idlesleep__pro=10000
60_remotecrawlloader_busysleep=40000
60_remotecrawlloader_busysleep__pro=2000
60_remotecrawlloader_memprereq=2097152
60_remotecrawlloader_isPaused=false
62_remotetriggeredcrawl_idlesleep=10000
62_remotetriggeredcrawl_busysleep=1000
62_remotetriggeredcrawl_memprereq=6291456
62_remotetriggeredcrawl_isPaused=false
70_cachemanager_idlesleep=1000
70_cachemanager_busysleep=1
70_cachemanager_memprereq=1048576
80_indexing_idlesleep=1000
80_indexing_busysleep=100
80_indexing_busysleep__pro=10
80_indexing_memprereq=6291456
82_crawlstack_idlesleep=5000
82_crawlstack_busysleep=1
82_crawlstack_memprereq=1048576
90_cleanup_idlesleep=300000
90_cleanup_busysleep=300000
90_cleanup_memprereq=0

