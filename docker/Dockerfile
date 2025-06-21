# Ubuntu Docker image for YaCy

# build with
# docker build -t yacy/yacy_search_server:latest -f Dockerfile ../

# run with
# docker run -d --name yacy -p 8090:8090 -p 8443:8443 -v yacy_data:/opt/yacy_search_server/DATA --log-opt max-size=200m --log-opt max-file=2 yacy/yacy_search_server:latest


## builder image
FROM eclipse-temurin:24-jdk-noble AS builder

# Install needed packages not in base image
RUN for i in 1 2 3; do \
      apt-get update && break || sleep 5; \
    done && \
    apt-get install -y --no-install-recommends ant git curl && \
    rm -rf /var/lib/apt/lists/*
    
# set current working dir & copy sources
WORKDIR /opt
COPY . /opt/yacy_search_server/
RUN ant compile -f /opt/yacy_search_server/build.xml

# Set initial admin password: "yacy" (encoded with custom yacy md5 function net.yacy.cora.order.Digest.encodeMD5Hex())
RUN sed -i "/adminAccountBase64MD5=/c\adminAccountBase64MD5=MD5:8cffbc0d66567a0987a4aba1ec46d63c" /opt/yacy_search_server/defaults/yacy.init && \
    sed -i "/adminAccountForLocalhost=/c\adminAccountForLocalhost=false" /opt/yacy_search_server/defaults/yacy.init && \
    sed -i "/server.https=false/c\server.https=true" /opt/yacy_search_server/defaults/yacy.init

## build final image
FROM eclipse-temurin:24-jdk-noble AS app
RUN for i in 1 2 3; do \
      apt-get update && break || sleep 5; \
    done && \
    apt-get install -y --no-install-recommends wkhtmltopdf xvfb ghostscript && \
    rm -rf /var/lib/apt/lists/*

LABEL maintainer="Michael Peter Christen <mc@yacy.net>"

# copy YaCy to app image
RUN adduser --system --group --no-create-home --disabled-password yacy
WORKDIR /opt
COPY --chown=yacy:yacy --from=builder /opt/yacy_search_server /opt/yacy_search_server

# Expose HTTP and HTTPS default ports
EXPOSE 8090 8443

# Set data volume: yacy data and configuration will persist even after container stop or destruction
VOLUME ["/opt/yacy_search_server/DATA"]

# Next commands run as yacy as non-root user for improved security
USER yacy

# Start yacy as a foreground process (-f) to display console logs and to wait for yacy process
CMD ["/bin/sh","/opt/yacy_search_server/startYACY.sh","-f"]

