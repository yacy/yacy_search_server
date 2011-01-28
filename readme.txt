README for YaCy (C) by Michael Peter Christen; mc@yacy.net
---------------------------------------------------------------------------
Please visit http://yacy.net for latest changes or new documentation.
YaCy comes with ABSOLUTELY NO WARRANTY!
This is free software, and you are welcome to redistribute it
under certain conditions; see file gpl.txt for details.
---------------------------------------------------------------------------

WHAT IS THIS?

YaCy is a search engine for the Web (supporting also ftp).
It is neither a search portal nor a portal software but a peer-to-peer
software that works on principles similar to file sharing.
The difference is that you do not share any kind of data but web indexes.
YaCy also generates the indexes it organizes. YaCy is a web crawler for 
everybody, without censorship and central data retention:
- search the web (automatically using all other YaCy peers)
- co-operative crawling; support for other crawlers
- intranet indexing and search
- set up your own search portal
- all users have equal rights
- comprehensive concept to anonymise the users' index
To be able to perform a search using the YaCy network, every user has to
set up their own node. More users are leading to higher index capacity
and better distributed indexing performance.


WHERE IS THE DOCUMENTATION?

Documentation can be found at:
(Home Page)     http://yacy.net/
(German Forum)  http://forum.yacy.de/
(Wiki:de)       http://www.yacy-websuche.de/wiki/index.php/De:Start
(Wiki:en)       http://www.yacy-websearch.net/wiki/index.php/En:Start

Every of these locations has a (YaCy) search functionality which combines
all these locations into one search result.


DEPENDENCIES? WHAT OTHER SOFTWARE DO I NEED?

You need java 1.5 or later to run YaCy.
Please download it from http://www.java.com

YaCy also runs on IcedTea6.
See http://icedtea.classpath.org

NO OTHER SOFTWARE IS REQUIRED!
(you don't need apache, tomcat or mysql or whatever)


HOW DO I START THIS SOFTWARE?

Startup and Shutdown of YaCy:

- on Linux:
to start: execute startYACY.sh
to stop : execute stopYACY.sh

- on Windows:
to start: double-click startYACY.bat
to stop : double-click stopYACY.bat

- on Mac OS X:
to start: double-click startYACY.command (alias possible!)
to stop : double-click stopYACY.command


HOW DO I USE THIS SOFTWARE, WHERE IS THE ADMINISTRATION INTERFACE?

YaCy is a server process that can be administrated and used
with your web browser: open

   http://localhost:8090

There you can see your personal search and administration interface.


ANY MORE CONFIGURATIONS?

- after startup, you see the configuration page in your web browser.
  just open http://localhost:8090
  all you have to do (should do) is to enter a password for your peer

- You can use YaCy as your web proxy.
  This is an option, you don't need to do that.
  Simply configure your internet connection to use a proxy at port 8090.



CONTACT:

If you have any questions, please do not hesitate to contact the author:
Send an email to Michael Christen (mc@yacy.net) with a meaningful subject
including the word 'yacy' to prevent that your email gets stuck
in my anti-spam filter.

If you like to have a customized version for special needs,
feel free to ask the author for a business proposal to customize YaCy
according to your needs. We also provide integration solutions if the
software is about to be integrated into your enterprise application.

Germany, Frankfurt a.M., 05.11.2010
Michael Peter Christen
