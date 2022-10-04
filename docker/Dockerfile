# Docker image for YaCy

# build with
# docker build -t yacy/yacy_search_server:latest -f Dockerfile ../

# run with
# docker run -d --name yacy -p 8090:8090 -p 8443:8443 -v yacy_data:/opt/yacy_search_server/DATA --log-opt max-size=200m --log-opt max-file=2 yacy/yacy_search_server:latest



## build app
FROM eclipse-temurin:8-jdk-focal AS appbuilder

RUN apt-get update && apt-get install -yq ant git curl wkhtmltopdf imagemagick xvfb ghostscript && rm -rf /var/lib/apt/lists/*
RUN java -version

WORKDIR /opt
COPY . /opt/yacy_search_server/

RUN ant compile -f /opt/yacy_search_server/build.xml && \
    apt-get purge -yq --auto-remove ant && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /opt/yacy_search_server/.git

# Set initial admin password: "yacy" (encoded with custom yacy md5 function net.yacy.cora.order.Digest.encodeMD5Hex())
RUN sed -i "/adminAccountBase64MD5=/c\adminAccountBase64MD5=MD5:8cffbc0d66567a0987a4aba1ec46d63c" /opt/yacy_search_server/defaults/yacy.init && \
    sed -i "/adminAccountForLocalhost=/c\adminAccountForLocalhost=false" /opt/yacy_search_server/defaults/yacy.init && \
    sed -i "/server.https=false/c\server.https=true" /opt/yacy_search_server/defaults/yacy.init



## build dist
FROM eclipse-temurin:8-jre-focal
LABEL maintainer="Michael Peter Christen <mc@yacy.net>"

RUN apt-get update && apt-get install -yq wkhtmltopdf imagemagick xvfb ghostscript && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
COPY . /opt/yacy_search_server/
COPY --from=appbuilder /opt/yacy_search_server /opt/yacy_search_server

RUN adduser --system --group --no-create-home --disabled-password yacy && chown yacy:yacy -R /opt/yacy_search_server
EXPOSE 8090 8443
VOLUME ["/opt/yacy_search_server/DATA"]
USER yacy
CMD ["/bin/sh","/opt/yacy_search_server/startYACY.sh","-f"]
