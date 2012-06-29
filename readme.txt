== WHAT IS THIS? ==
YaCy is a search engine software. It takes a new approach to search
because it does not use a central server. Instead, its search results
come from a network of independent peers. In such a distributed network,
no single entity decides what gets listed, or in which order results appear.

The YaCy search engine runs on each user's own computer. Search terms are
hashed before they leave the user's computer. Different from conventional
search engines, YaCy is designed to protect the users' privacy.
A user's computer can create with YaCy its individual search indexes and
rankings, so that results better match what the user is looking for over time.
YaCy also makes it easy to create a customized search portal with a few clicks.

Each YaCy user is either part of a large search network (YaCy contains a
peer-to-peer network protocol to exchange search indexes with other YaCy
search engine installations) or the user runs YaCy to produce
a personal search portal that can be either public or private.

YaCy search portals can also be placed in intranet environment which makes
YaCy a replacement for commercial enterprise search solutions. A network
scanner makes it easy to discover all available http, ftp and smb servers.

To create a web index, YaCy has a web crawler for 
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


== LICENSE ==
YaCy is published under the GPL v2
The source code is inside the release package (see /source and /htroot).


== WHERE IS THE DOCUMENTATION? ==
Documentation can be found at:
(Home Page)        http://yacy.net/
(German Forum)     http://forum.yacy.de/
(Wiki:de)          http://www.yacy-websuche.de/wiki/index.php/De:Start
(Wiki:en)          http://www.yacy-websearch.net/wiki/index.php/En:Start
(Tutorial Videos)  http://yacy.net/en/Tutorials.html and http://yacy.net/de/Lehrfilme.html

Every of these locations has a (YaCy) search functionality which combines
all these locations into one search result.


== DEPENDENCIES? WHAT OTHER SOFTWARE DO I NEED? ==
You need java 1.6 or later to run YaCy, nothing else.
Please download it from http://www.java.com

YaCy also runs on IcedTea6.
See http://icedtea.classpath.org

NO OTHER SOFTWARE IS REQUIRED!
(you don't need apache, tomcat or mysql or whatever)


== HOW DO I START THIS SOFTWARE? ==
Startup and Shutdown of YaCy:

- on GNU/Linux:
to start: execute ./startYACY.sh
to stop : execute ./stopYACY.sh

- on Windows:
to start: double-click startYACY.bat
to stop : double-click stopYACY.bat

- on Mac OS X:
please use the Mac Application and start or stop it like any
other Mac Application (doubleclick to start)


== HOW DO I USE THIS SOFTWARE, WHERE IS THE ADMINISTRATION INTERFACE? ==
YaCy is a build on a web server. After you started YaCy,
start your browser and open

   http://localhost:8090

There you can see your personal search and administration interface.


== WHAT IF I INSTALL YACY (HEADLESS) ON A SERVER ==
You can do that but YaCy authorizes users automatically if they
access the server from the localhost. After about 10 minutes a random
password is generated and then it is not possible to log in from
a remote location. If you install YaCy on a server that is not your
workstation, then you must set an administration account immediately
after the first start-up. Open:

http://<remote-server-address>:8090/ConfigAccounts_p.html

and set an administration account.


== PORT 8090 IS BAD, PEOPLE ARE NOT ALLOWED TO ACCESS THAT PORT ==
You can forward port 80 to 8090 with iptables:
iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8090
On some operation systems, you must first enable access to the ports you are using like:
iptables -I INPUT -m tcp -p tcp --dport 8090 -j ACCEPT


== HOW CAN I SCALE THIS; HOW MUCH RAM IS NEEDED; DISK SPACE? ==
YaCy can scale up to many millions of web pages in your own search index.
The default assignment of RAM is 600MB which is assigned to the java
process but not permanently used by it. The GC process will free the memory
once in a while. If you have a small index (i.e. about 100000 pages)
then you may assign _less_ memory (i.e. 200MB) but if your index scales
up to over 1 million web pages then you should start to increase the
memory assignment. Open http://localhost:8090/Performance_p.html
and set a higher/lower memory assignment.
If you have millions of web pages in your search index then you might
habe gigabytes of disk space allocated. You can reduce the disk
space i.e. setting the htcache space to a different size; to do that
open http://localhost:8090/ConfigHTCache_p.html and set a new size.



== JOIN THE DEVELOPMENT! ==
YaCy was created with the help of many. About 30 programmers have helped,
a list of some of them can be seen here: http://yacy.net/en/Join.html
Please join us!


== HOW TO GET THE SOURCE CODE AND HOW TO COMPILE YACY YOURSELF? ==
The source code is inside every YaCy release. You can also get YaCy
from https://gitorious.org/yacy/rc1
Please clone our code and help with development!
The code is licensed under the GPL v2.

Compiling YaCy:
- you need java 1.6 and ant
- just compile: "ant clean all" - then you can "./startYACY.sh"
- create a release tarball: "ant dist"
- create a Mac OS release: "ant distMacApp" (works only on a Mac)
- create a debian release: "ant deb"
- work with eclipse: within eclipse you also need to start the ant build process
  because the servlet pages are not compiled by the eclipse build process
after the dist prodecure, the release can be found in the RELEASE subdirectory


== ARE THERE ANY APIs OR HOW CAN I ATTACH SOFTWARE AT YACY? ==
There are many interfaces build-in in YaCy and they are all based on http/xml and
http/json. You can discover these interfaces if you notice the orange "API" icon in
the upper right of some web pages in the YaCy web interface. Just click on it and
you will see the xml/json version of the information you just have seen at the web
page.
A different approach is the usage of the shell script provided in the /bin
subdirectory. The just call also the web interface pages. By cloning some of those
scripts you can create more shell api access methods yourself easily.


== CONTACT ==
Our primary point of contact is the german forum at http://forum.yacy.net
There is also an english forum at http://www.yacy-forum.org
We encourage you to start a YaCy forum in your own language.

If you have any questions, please do not hesitate to contact the maintainer:
Send an email to Michael Christen (mc@yacy.net) with a meaningful subject
including the word 'yacy' to prevent that your email gets stuck
in my anti-spam filter.

If you like to have a customized version for special needs,
feel free to ask the author for a business proposal to customize YaCy
according to your needs. We also provide integration solutions if the
software is about to be integrated into your enterprise application.

Germany, Frankfurt a.M., 26.11.2011
Michael Peter Christen
