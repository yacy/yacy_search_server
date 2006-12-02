README for YaCy (C) by Michael Peter Christen; mc@anomic.de
---------------------------------------------------------------------------
Please visit www.yacy.net for latest changes or new documentation.
YaCy comes with ABSOLUTELY NO WARRANTY!
This is free software, and you are welcome to redistribute it
under certain conditions; see file gpl.txt for details.
---------------------------------------------------------------------------

WHAT IS THIS?

This is a Peer-to-Peer - based Web Search Engine.
There is no search central, the YaCy users create a web search network.
You can also use this software to set up your own search portal.


WHERE IS THE DOCUMENTATION?

The complete documentation can be found at:
(English)  http://yacy.net/yacy
(Deutsch)  http://www.yacy-websuche.de
(Wiki:de)  http://www.yacy-websuche.de/wiki/index.php/De:Start
(Wiki:en)  http://www.yacy-websearch.net/wiki/index.php/En:Start


WHAT CAN I DO WITH THIS SOFTWARE?

- search the web (automatically using all other YaCy peers)
- crawl the web (and you contribute to the global web index)
- set up your own search portal
- use it as your personal web server
- use it as your web proxy (..and visited pages are indexed)
- many more


DEPENDENCIES? WHAT OTHER SOFTWARE DO I NEED?

You need java 1.4.2 or later to run YaCy.
Please download it from http://java.sun.com
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

   http://localhost:8080

There you can see your personal search and administration interface.


ANY MORE CONFIGURATIONS?

- after startup, you see the configuration page in your web browser.
  just open http://localhost:8080
  all you have to do (should do) is to enter a password for your peer
- You can use YaCy as your web proxy. But you don't need to do that.
  Simply configure your internet connection to use a proxy at port 8080.
- You can add a YaCy toolbar to your Firefox web browser.
  This release contains the yacybar.xpi file from Alexander Schier
  and Martin Thelian. Please install this file as a Firefox extension.


CONTACT:

If you have any questions, please do not hesitate to contact the author:
Send an email to Michael Christen (mc@anomic.de) with a meaningful subject
including the word 'yacy' to prevent that your email gets stuck
in my anti-spam filter.

If you like to have a customized version for special needs,
feel free to ask the author for a business proposal to customize YaCy
according to your needs. We also provide integration solutions if the
software is about to be integrated into your enterprise application.

Germany, Frankfurt a.M., 02.12.2006
Michael Peter Christen
