# Docker image for YaCy

# build with
# docker build -t yacy/yacy_search_server:latest -f Dockerfile ../

# run with
# docker run -d --name yacy -p 8090:8090 -p 8443:8443 -v yacy_data:/opt/yacy_search_server/DATA --log-opt max-size=200m --log-opt max-file=2 yacy/yacy_search_server:latest


## builder image
FROM eclipse-temurin:24-jdk-noble AS builder
RUN for i in 1 2 3; do \
      apt-get update && break || sleep 5; \
    done && \
    apt-get install -y --no-install-recommends ant git curl && \
    rm -rf /var/lib/apt/lists/*
    
# compile YaCy
WORKDIR /opt
COPY . /opt/yacy_search_server/
RUN ant compile -f /opt/yacy_search_server/build.xml

# Set initial admin password: "yacy" (encoded with custom yacy md5 function net.yacy.cora.order.Digest.encodeMD5Hex())
# Intially enable HTTPS: this is the most secure option for remote administrator authentication
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

EXPOSE 8090 8443
VOLUME ["/opt/yacy_search_server/DATA"]
USER yacy
CMD ["/bin/sh","/opt/yacy_search_server/startYACY.sh","-f"]

