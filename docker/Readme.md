# Yacy Docker image from latest sources

[![Deploy to Docker Cloud](https://files.cloud.docker.com/images/deploy-to-dockercloud.svg)](https://cloud.docker.com/stack/deploy/?repo=https://github.com/luccioman/yacy_search_server/tree/docker/docker)

## Getting built image from Docker Hub

	docker pull luccioman/yacy
	
Repository URL : (https://hub.docker.com/r/luccioman/yacy/)

## Building image yourself

Using yacy_search_server/docker/Dockerfile :

	cd yacy_search_server/docker
	docker build .
	
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

	docker run --name yacy -p 8090:8090 luccioman/yacy
	
--name option allow easier management of your container (without it, docker automatically generate a new name at each startup).

-p option map host port and container port, allowing web interface access through the usual http://localhost:8090.

#### With persistent data volume

	docker run -v [your_host/data/directory]:/opt/yacy_search_server/DATA luccioman/yacy
		
This allow your container to reuse a data directory form the host.

#### As background process

	docker run -d luccioman/yacy

### Next starts

#### As attached process

	docker start -a yacy
	
#### As background process

	docker start yacy

### Shutdown

* Use "Shutdown" button in administration web interface
