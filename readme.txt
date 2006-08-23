README for YaCy (C) by Michael Peter Christen; mc@anomic.de
---------------------------------------------------------------------------
Please visit www.yacy.net for latest changes or new documentation.
YaCy comes with ABSOLUTELY NO WARRANTY!
This is free software, and you are welcome to redistribute it
under certain conditions; see file gpl.txt for details.
---------------------------------------------------------------------------

This is a P2P-based Web Search Engine
and also a caching http/https proxy.
You can use it to set up your own search portal.

The complete documentation can be found at:
(English)    http://yacy.net/yacy
(Deutsch)    http://www.yacy-websuche.de
(Wiki:de/en) http://www.yacy-websuche.de/wiki

YOU NEED JAVA 1.4.2 OR LATER TO RUN THIS APPLICATION!
PLEASE DOWNLOAD JAVA FROM http://java.sun.com

NO OTHER SOFTWARE IS REQUIRED! (you don't need apache, tomcat or mysql)


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

- on any other OS:
to start: execute java as
          java -classpath classes:htroot:lib/commons-collections.jar:lib/commons-pool-1.2.jar yacy -startup <yacy-release-path>
to stop : execute java as
          java -classpath classes:htroot:lib/commons-collections.jar:lib/commons-pool-1.2.jar yacy -shutdown


YaCy is a server process that can be administrated and used
with your web browser:
browse to http://localhost:8080 where you can see your personal
search, configuration and administration interface.

If you want to use the built-in proxy, simply configure your internet connection
to use a proxy at port 8080. You can also change this default proxy port.

If you like to use YaCy not as proxy but only as distributed
crawling/search engine, you can do so.
Start crawling at the 'Index Creation' menu point.

You can add a YaCy toolbar to your Firefox web browser.
This release contains the yacybar.xpi file from Alexander Schier and Martin Thelian.
Please install this file as a Firefox extension.

If you have any questions, please do not hesitate to contact the author:
Send an email to Michael Christen (mc@anomic.de) with a meaningful subject
including the word 'yacy' to prevent that your email gets stuck
in my anti-spam filter.

If you like to have a customized version for special needs,
feel free to ask the author for a business proposal to customize YaCy
according to your needs. We also provide integration solutions if the
software is about to be integrated into your enterprise application.

Germany, Frankfurt a.M., 23.08.2006
Michael Peter Christen
