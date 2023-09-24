# Yacy Docker image from latest sources

## Supported tags and respective Dockerfiles

* latest (Dockerfile)
* latest-alpine (Dockerfile.alpine)

## Getting built image from Docker Hub

The repository URL is https://hub.docker.com/r/yacy/yacy_search_server/

* ubuntu-based: `docker pull yacy/yacy_search_server:latest`


## Building image yourself

Using files in 'yacy_search_server/docker/':
```
cd yacy_search_server/docker
```

Then according to the image type:
* `yacy/yacy_search_server:latest`: This image is based on latest stable official Debian stable [openjdk](https://hub.docker.com/_/openjdk/) 11 image provided by Docker. Embed Yacy compiled from latest git repository sources.

```
docker build -t yacy/yacy_search_server:latest -f Dockerfile ../
```

* `yacy/yacy_search_server:aarch64-latest`: same as yacy/yacy_search_server:latest but based on 

```
docker build -t yacy/yacy_search_server:aarch64-latest -f Dockerfile.aarch64 ../
```



## Usage

### Run the docker image


```
docker run -d --name yacy -p 8090:8090 -p 8443:8443 -v yacy_data:/opt/yacy_search_server/DATA --log-opt max-size=200m --log-opt max-file=2 yacy/yacy_search_server:latest
```

YaCy web interface is then exposed at http://[container_ip]:8090
You can retrieve the container IP address with `docker inspect`.

#### Default admin account

* login: admin
* password: yacy

You should modify this default password with page /ConfigAccounts_p.html when exposing publicly your YaCy container.


#### Handle persistent data volume

As configured in the Dockerfile, by default yacy data (in /opt/yacy_search_server/DATA) will persist after container stop or deletion, in a volume named "yacy_data"

    
### HTTPS support

This images are default configured with HTTPS enabled, and use a default certificate stored in defaults/freeworldKeystore. You should use your own certificate. In order to do it, you can proceed as follow.

#### Self-signed certificate

A self-signed certificate will provide encrypted communications with your YaCy server, but browsers will still complain about an invalid security certificate with the error "SEC_ERROR_UNKNOWN_ISSUER". If it is sufficient for you, you can permanently add and exception to your browser.

This kind of certificate can be generated and added to your YaCy Docker container with the following:

    keytool -keystore /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacykeystore -genkey -keyalg RSA -alias yacycert
    
Then edit YaCy config file. For example with the nano text editor:

    nano /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacy.conf

And configure the keyStoreXXXX properties accordingly:

    keyStore=/opt/yacy_search_server/DATA/SETTINGS/yacykeystore
    keyStorePassword=yourpassword
    
#### Import an existing certificate:

Importing a certificate validated by a certification authority (CA) will ensure you have full HTTPS support with no security errors when accessing your YaCy peer. You can import an existing certificate in pkcs12 format.

First copy it to the YaCy Docker container volume:

    cp [yourStore].pkcs12 /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/[yourStore].pkcs12

Then edit YaCy config file. For example with the nano text editor:

    nano /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacy.conf

And configure the pkcs12XXX properties accordingly:

    pkcs12ImportFile=/opt/yacy_search_server/DATA/SETTINGS/[yourStore].pkcs12
    pkcs12ImportPwd=yourpassword

### Next starts

#### As attached process

    docker start -a yacy

#### As background process

    docker start yacy

### Shutdown

* Use "Shutdown" button in administration web interface
* OR run:

    docker exec [your_container_name] /opt/yacy_search_server/stopYACY.sh

* OR run:

    docker stop [your_container_name]

### Upgrade

You can upgrade your YaCy container the Docker way with the following commands sequence.

Get latest Docker image:

    docker pull yacy/yacy_search_server:latest

Create new container based on pulled image, using volume data from old container:

    docker create --name [tmp-container_name] -p 8090:8090 -p 8443:8443 --volumes-from=[container_name] --log-opt max-size=100m --log-opt max-file=2 yacy/yacy_search_server:latest

Stop old container:

    docker exec [container_name] /opt/yacy_search_server/stopYACY.sh

Start new container:

    docker start [tmp-container_name]

Check everything works fine, then you can delete old container:

    docker rm [container_name]

Rename new container to reuse same container name:

    docker rename [tmp-container_name] [container_name]

## License

View [license](https://github.com/yacy/yacy_search_server/blob/master/COPYRIGHT) information for the software contained in this image.
