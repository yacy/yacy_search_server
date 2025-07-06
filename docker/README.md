# Yacy Docker image from latest sources

## Supported tags

* `latest` (Latest stable release)
* `latest-alpine` (Latest stable release based on Alpine Linux)
* `<version>` (Lock on a specific/minor/major version, e.g., `1.2.3`, `1.2`, `1`)
* `<version>-alpine` (e.g., `1.2.3-alpine`)
* `<branch-name>` (e.g., `master` for the latest commit of branches)
* `pr-<number>` (e.g., `pr-42` for pull requests)

For a detailed explanation of supported tags, refer to [DockerImageTags.md](./DockerImageTags.md).

## Getting built image from Docker Hub and GitHub Container Registry

The repository URLs are:

* Docker Hub: <https://hub.docker.com/r/yacy/yacy_search_server/>
* GitHub Container Registry: `ghcr.io/yacy/yacy_search_server`

Examples:

* `docker pull yacy/yacy_search_server:latest`
* `docker pull ghcr.io/yacy/yacy_search_server:latest`
* `docker pull ghcr.io/yacy/yacy_search_server:latest-alpine`
* `docker pull ghcr.io/yacy/yacy_search_server:1.2.3`
* `docker pull ghcr.io/yacy/yacy_search_server:master`
* `docker pull ghcr.io/yacy/yacy_search_server:pr-42`

## Building image yourself

Using files in 'yacy_search_server/docker/':

```sh
cd yacy_search_server/docker
```

Then according to the image type:

* `yacy/yacy_search_server:latest`: This image is based on the latest stable official [Eclipse Temurin](https://hub.docker.com/_/eclipse-temurin) 21 release. It includes YaCy compiled from the latest git repository sources.
* `yacy/yacy_search_server:latest-alpine`: This image is based on the latest stable [Eclipse Temurin](https://hub.docker.com/_/eclipse-temurin) 21 release with Alpine Linux as the base. It also includes YaCy compiled from the latest git repository sources.

```sh
docker build -t yacy/yacy_search_server:latest -f Dockerfile ../
```

## Usage

### Run the Docker image directly

You can run the YaCy Docker image directly using the following command:

```sh
docker run -d --name yacy -p 8090:8090 -p 8443:8443 -v yacy_data:/opt/yacy_search_server/DATA --log-opt max-size=200m --log-opt max-file=2 yacy/yacy_search_server:latest
```

### Using Docker Compose

You can also use Docker Compose to run the YaCy container. Create a `docker-compose.yml` file with the following content:

```yaml
services:
    yacy:
        container_name: yacy
        image: yacy/yacy_search_server:latest
        restart: unless-stopped
        ports:
            - "8090:8090"
            - "8443:8443"
        volumes:
            - yacy_data:/opt/yacy_search_server/DATA
        logging:
            options:
                max-size: "200m"
                max-file: "2"

volumes:
    yacy_data:
```

Then start the container with:

```sh
docker-compose up -d
```

YaCy web interface is then exposed at `http://[container_ip]:8090`  
You can retrieve the container IP address with `docker inspect`.

### Default admin account

* login: admin
* password: yacy

You should modify this default password with page /ConfigAccounts_p.html when exposing publicly your YaCy container.

### Handle persistent data volume

As configured in the Dockerfile, by default YaCy data (in /opt/yacy_search_server/DATA) will persist after container stop or deletion, in a volume named "yacy_data".

### HTTPS support

This images are default configured with HTTPS enabled, and use a default certificate stored in defaults/freeworldKeystore. You should use your own certificate. In order to do it, you can proceed as follow.

#### Self-signed certificate

A self-signed certificate will provide encrypted communications with your YaCy server, but browsers will still complain about an invalid security certificate with the error "SEC_ERROR_UNKNOWN_ISSUER". If it is sufficient for you, you can permanently add an exception to your browser.

This kind of certificate can be generated and added to your YaCy Docker container with the following:

```sh
keytool -keystore /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacykeystore -genkey -keyalg RSA -alias yacycert
```

Then edit YaCy config file. For example, with the nano text editor:

```sh
nano /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacy.conf
```

And configure the keyStoreXXXX properties accordingly:

```sh
keyStore=/opt/yacy_search_server/DATA/SETTINGS/yacykeystore
keyStorePassword=yourpassword
```

#### Import an existing certificate

Importing a certificate validated by a certification authority (CA) will ensure you have full HTTPS support with no security errors when accessing your YaCy peer. You can import an existing certificate in pkcs12 format.

First copy it to the YaCy Docker container volume:

```sh
cp [yourStore].pkcs12 /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/[yourStore].pkcs12
```

Then edit YaCy config file. For example, with the nano text editor:

```sh
nano /var/lib/docker/volumes/[your_yacy_volume]/_data/SETTINGS/yacy.conf
```

And configure the pkcs12XXX properties accordingly:

```sh
pkcs12ImportFile=/opt/yacy_search_server/DATA/SETTINGS/[yourStore].pkcs12
pkcs12ImportPwd=yourpassword
```

### Next starts

#### As attached process

```sh
docker start -a yacy
```

#### As background process

```sh
docker start yacy
```

### Shutdown

* Use "Shutdown" button in administration web interface
* OR run:

```sh
docker exec yacy /opt/yacy_search_server/stopYACY.sh
```

* OR run:

```sh
docker stop yacy
```

### Upgrade

To upgrade your YaCy container, follow these steps:

1. Pull the latest Docker image:

    ```sh
    docker pull yacy/yacy_search_server:latest
    ```

2. Stop and remove the old container:

    ```sh
    docker stop yacy
    docker rm yacy
    ```

3. Run the new container using the same command as before:

    ```sh
    docker run -d --name yacy -p 8090:8090 -p 8443:8443 -v yacy_data:/opt/yacy_search_server/DATA --log-opt max-size=200m --log-opt max-file=2 yacy/yacy_search_server:latest
    ```

#### Update Docker Compose Images

To update the images created with the Docker Compose file, you can use the following command:

```sh
docker-compose up -d --force-recreate --pull always
```

This command ensures that the latest images are pulled and the containers are recreated with the updated images.

## License

View [license](https://github.com/yacy/yacy_search_server/blob/master/COPYRIGHT) information for the software contained in this image.
