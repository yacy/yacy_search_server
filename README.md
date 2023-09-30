<div align="center">
<h1 align="center">YaCy</h1>

Search Engine Software

[![Gitter](https://badges.gitter.im/yacy/yacy_search_server.svg)](https://gitter.im/yacy/yacy_search_server?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://github.com/yacy/yacy_search_server/actions/workflows/ant-build-selfhosted.yaml/badge.svg)](https://github.com/yacy/yacy_search_server/actions/workflows/ant-build-selfhosted.yaml)
[![Install Link](https://img.shields.io/badge/install-stable-blue.svg)](https://yacy.net/download_installation/)

![Web Search](./screenshots/screenshot_web_search.png)
![Crawl Start](./screenshots/screenshot_expert_crawl_start.png)
![Index Browser](./screenshots/screenshot_index_browser.png)
</div>


## What is YaCy?

YaCy is a full search engine application containing a server hosting a search index,
a web application to provide a nice user front-end for searches and index creation
and a production-ready web crawler with a scheduler to keep a search index fresh.

YaCy search portals can also be placed in an intranet environment, making
it a replacement for commercial enterprise search solutions. A network
scanner makes it easy to discover all available HTTP, FTP and SMB servers.

Running a personal Search Engine is a great tool for privacy; indeed YaCy
was created with the privacy aspect as priority motivation for the project.

You can also use YaCy with a customized search page in your own web applications.

## Large-Scale Web Search with a Peer-to-Peer Network

Each YaCy peer can be part of a large search network where search indexes can be
exchanged with other YaCy installation over a built-in peer-to-peer network protocol.

This is the default operation that enables new users to instantly access
a large-scale search cluster, operated only by YaCy users.

You can opt-out from the YaCy cluster operation by choosing a different operation
mode in the web interface. You can also opt-out from the network in individual searches,
turning the use of YaCy a completely privacy-aware tool - in this operation mode search
results are computed from the local index only.

## License

This project is available as open source under the terms of the GPL 2.0 or later. However, some elements are being licensed under GNU Lesser General Public License. For accurate information, please check individual files. As well as for accurate information regarding copyrights.
The (GPLv2+) source code used to build YaCy is distributed with the package (in /source and /htroot).


## Where is the documentation?

- [Homepage](https://yacy.net)
- [International Forum](https://community.searchlab.eu)
- [Documentation / FAQ](https://yacy.net/faq/)
- [English wiki](https://wiki.yacy.net/index.php/En:Start)
- [German wiki](https://wiki.yacy.net/index.php/De:Start)
- [Esperanto wiki](https://wiki.yacy.net/index.php/Eo:Start)
- [French wiki](https://wiki.yacy.net/index.php/Fr:Start)
- [Spanish wiki](https://wiki.yacy.net/index.php/Es:Start)
- [Russian wiki](https://wiki.yacy.net/index.php/Ru:Start)
- [Video tutorials](https://www.youtube.com/@YaCyTutorials/videos)
- [javadoc documentation](https://yacy.net/api/javadoc/) for developers

All these have (YaCy) search functionality combining all these locations into one search result.

## Dependencies? What other software do I need?

You need Java 11 or later to run YaCy. (No Apache, Tomcat or MySQL or anything else)

YaCy also runs on IcedTea 3.
See https://icedtea.classpath.org

## Start and stop it

Startup and shutdown:

- GNU/Linux and OpenBSD:
   - Start by running `./startYACY.sh`
   - Stop by running `./stopYACY.sh`

- Windows:
   - Start by double-clicking `startYACY.bat`
   - Stop by double-clicking `stopYACY.bat`

- macOS:
Please use the Mac app and start or stop it like any
other program (double-click to start)


## The administration interface

A web server is brought up after starting YaCy.
Open this URL in your web-browser:

   http://localhost:8090

This presents you with the personal search and administration interface.


## (Headless) YaCy server installation

YaCy will authorize users automatically if they
access the server from its localhost. After about 10 minutes a random
password is generated, and then it is no longer possible to log in from
a remote location. If you install YaCy on a server that is not your
workstation you must set an admin account immediately after the first start-up.
Open:

    http://<remote-server-address>:8090/ConfigAccounts_p.html

and set an admin account.

## YaCy in a virtual machine or a container

Use virtualization software like VirtualBox or VMware. 

The following container technologies can deploy locally, on remote machines you own, or in the 'cloud' using a provider by clicking "Deploy" at the top of the page:

### Docker

More details in the [docker/Readme.md](docker/Readme.md).

### [Heroku](https://www.heroku.com/)

PaaS (Platform as a service)
More details in [Heroku.md](Heroku.md).

## Port 8090 is bad, people are not allowed to access that port

You can forward port 80 to 8090 with iptables:
```bash
iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8090
```

On some operating systems, access to the ports you are using must be granted first:
```bash
iptables -I INPUT -m tcp -p tcp --dport 8090 -j ACCEPT
```

## Scaling, RAM and disk space

You can have many millions web pages in your own search index.
By default, 600MB RAM is available to the Java process.
The GC process will free the memory once in a while. If you have less than
100000 pages you could try 200MB till you hit 1 million.
[Here](http://localhost:8090/Performance_p.html) you can adjust it.
Several million web pages may use several GB of disk space, but you can
adjust it [here](http://localhost:8090/ConfigHTCache_p.html) to fit your needs.


## Help develop YaCy

Join the large number of contributors that make YaCy what it is;
community software.

To start developing YaCy in **Eclipse**:

- clone https://github.com/yacy/yacy_search_server.git using build-in Eclipse features (File -> Import -> Git) 
- or download source from this site (download button "Code" -> download as Zip -> and unpack)
- Open Help -> Eclipse Marketplace -> Search for "ivy" -> Install "Apache IvyDE"
- right-click on the YaCy project in the package explorer -> Ivy -> resolve

This will build YaCy in Eclipse. To run YaCy:
- Package Explorer -> YaCy: navigate to source -> net.yacy
- right-click on yacy.java -> Run as -> Java Application


To start developing YaCy in **Netbeans**:

- clone https://github.com/yacy/yacy_search_server.git (Team → Git → Clone)
    - if you checked "scan for project" you'll be asked to open the project
- Open the project (File → Open Project)
- you may directly use all the Netbeans build feature.

To join our development community, got to https://community.searchlab.eu

Send pull requests to https://github.com/yacy/yacy_search_server


## Compile from source

The source code is bundled with every YaCy release. You can also get YaCy
from https://github.com/yacy/yacy_search_server by cloning the repository.

```
git clone https://github.com/yacy/yacy_search_server
```

Compiling YaCy:
- You need Java 11, ivy and ant
- See `ant -p` for the available ant targets
```
ant clean dist
```
resulting tar.gz with YaCy package will be located in RELEASE/ directory.
Move it into desired location and unpack with:
```
tar zfvx yacy_v1.version_release_number_different_each_time.tar.gz
``` 

## APIs and attaching software

YaCy has many built-in interfaces, and they are all based on HTTP/XML and
HTTP/JSON. You can discover these interfaces if you notice the orange "API" icon in
the upper right corner of some web pages in the YaCy web interface. Click it, and
you will see the XML/JSON version of the respective webpage.
You can also use the shell script provided in the /bin subdirectory.
The shell scripts also call the YaCy web interface. By cloning some of those
scripts you can easily create more shell API access methods.

## Contact

[Visit the international YaCy forum](https://community.searchlab.eu)
where you can start a discussion there in your own language.

Questions and requests for paid customization and integration into enterprise solutions.
can be sent to the maintainer, Michael Christen per e-mail (at mc@yacy.net)
with a meaningful subject including the word 'YaCy' to prevent it getting stuck in the spam filter.

- Michael Peter Christen
