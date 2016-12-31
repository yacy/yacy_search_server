# Yacy Docker image from latest sources

## Supported tags and respective Dockerfiles

* latest (Dockerfile)
* lastet-alpine (Dockerfile.alpine)

## Getting built image from Docker Hub

	docker pull luccioman/yacy
	
Repository URL : (https://hub.docker.com/r/luccioman/yacy/)

## Building image yourself

Using yacy_search_server/docker/Dockerfile :

	cd yacy_search_server/docker
	docker build .
	
To build the Alpine variant :

	cd yacy_search_server/docker
	docker build -f Dockerfile.alpine .
	
## Image variants

`luccioman/yacy:latest`

This image is based on latest stable official Debian [java](https://hub.docker.com/_/java/) image provided by Docker. Embed Yacy compiled from latest git repository sources.

`luccioman/yacy:latest-alpine`

This image is based on latest stable official Alpine Linux [java](https://hub.docker.com/_/java/) image provided by Docker. Embed Yacy compiled from latest git repository sources.
	
## Default admin account

login : admin

password : docker

You should modify this default password with page /ConfigAccounts_p.html when exposing publicly your YaCy container.

## Usage

### First start

#### Most basic

	docker run luccioman/yacy

YaCy web interface is then exposed at http://[container_ip]:8090.	
You can retrieve the container IP address with `docker inspect`.

#### Easier to handle

	docker run --name yacy -p 8090:8090 -p 8443:8443 --log-opt max-size=200m --log-opt max-file=2 luccioman/yacy
	
##### Options detail
	
* --name : allow easier management of your container (without it, docker automatically generate a new name at each startup).
* -p 8090:8090 -p 8443:8443 : map host ports to YaCy container ports, allowing web interface access through the usual http://localhost:8090 and https://localhost:8443 (you can set a different mapping, for example -p 443:8443 if you prefer to use the default HTTPS port on your host)
* --log-opt max-size : limit maximum docker log file size for this container
* --log-opt max-file : limit number of docker rotated log files for this container

Note : if you do not specify the log related options, when running a YaCy container 24hour a day with default log level, your Docker container log file will grow up to some giga bytes in a few days!

#### Handle persistent data volume

As configured in the Dockerfile, by default yacy data (in /opt/yacy_search_server/DATA) will persist after container stop or deletion, in a volume with an automatically generated id.

But you may map a host directory to hold yacy data in container :

	docker run -v [/your_host/data/directory]:/opt/yacy_search_server/DATA luccioman/yacy
	
Or just use a volume label to help identify it later

	docker run -v yacy_volume:/opt/yacy_search_server/DATA luccioman/yacy

Note that you can list all docker volumes with :

	docker volume ls

#### Start as background process

	docker run -d luccioman/yacy
	
### HTTPS support

This images are default configured with HTTPS enabled, and use a default certificate stored in defaults/freeworldKeystore. You should use your own certificate. In order to do it, you can proceed as follow.

#### Self-signed certificate

A self-signed certificate will provide encrypted communications with your YaCy server, but browsers will still complain about an invalid security certificate with the error "SEC_ERROR_UNKNOWN_ISSUER". If it is sufficient for you, you can permanently add and exception to your browser.

This kind of certificate can be generated and added to your YaCy Docker container with the following :

	keytool -keystore /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacykeystore -genkey -keyalg RSA -alias yacycert
	
Then edit YaCy config file. For example with the nano text editor :

	nano /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacy.conf

And configure the keyStoreXXXX properties accordingly :

	keyStore=/opt/yacy_search_server/DATA/SETTINGS/yacykeystore
	keyStorePassword=yourpassword
	
#### Import an existing certificate:

Importing a certificate validated by a certification authority (CA) will ensure you have full HTTPS support with no security errors when accessing your YaCy peer. You can import an existing certificate in pkcs12 format.

First copy it to the YaCy Docker container volume :

	cp [yourStore].pkcs12 /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/[yourStore].pkcs12
	
Then edit YaCy config file. For example with the nano text editor :

	nano /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacy.conf

And configure the pkcs12XXX properties accordingly :

	pkcs12ImportFile=/opt/yacy_search_server/DATA/SETTINGS/[yourStore].pkcs12
	pkcs12ImportPwd=yourpassword

### Next starts

#### As attached process

	docker start -a yacy
	
#### As background process

	docker start yacy

### Shutdown

* Use "Shutdown" button in administration web interface
* OR run :

	docker exec [your_container_name] /opt/yacy_search_server/stopYACY.sh
	
* OR run :

	docker stop [your_container_name]
	
### Upgrade

You can upgrade your YaCy container the Docker way with the following commands sequence.

Get latest Docker image :

	docker pull luccioman/yacy:latest
OR 
	docker pull luccioman/yacy:latest-alpine
	
Create new container based on pulled image, using volume data from old container :
	
	docker create --name [tmp-container_name] -p 8090:8090 -p 8443:8443 --volumes-from=[container_name] --log-opt max-size=100m --log-opt max-file=2 luccioman/yacy:latest
	
Stop old container :

	docker exec [container_name] /opt/yacy_search_server/stopYACY.sh
	

Start new container :

	docker start [tmp-container_name]
	
Check everything works fine, then you can delete old container :
	
	docker rm [container_name]
	
Rename new container to reuse same container name :

	docker rename [tmp-container_name] [container_name]

## License

View [license](https://github.com/yacy/yacy_search_server/blob/master/COPYRIGHT) information for the software contained in this image.