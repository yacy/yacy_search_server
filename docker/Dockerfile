# Build a docker image from latest YaCy sources

# Base image : latest stable Debian 
FROM debian:latest

# Install needed packages
RUN apt-get update && apt-get install -yq \
	default-jdk \
	default-jre-headless \
	ant \
	git
	
# set current working dir
WORKDIR /opt

# clone main YaCy git repository (we need to clone git repository to generate correct version when building from source)
RUN git clone https://github.com/yacy/yacy_search_server.git

# trace content of source directory
RUN ls -la /opt/yacy_search_server

# set current working dir
WORKDIR /opt/yacy_search_server
	
# Compile with ant
RUN ant compile

# Set initial admin password : "docker" (encoded with custom yacy md5 function net.yacy.cora.order.Digest.encodeMD5Hex())
RUN sed -i "/adminAccountBase64MD5=/c\adminAccountBase64MD5=MD5:e672161ffdce91be4678605f4f4e6786" /opt/yacy_search_server/defaults/yacy.init

# make some cleaning to reduce image size
RUN rm -rf .git \
	&& apt-get purge -yq --auto-remove \
		default-jdk \
		ant \
		git \ 
	&& apt-get clean

# Expose port 8090
EXPOSE 8090

# Set data volume : can be used to persist yacy data and configuration
VOLUME ["/opt/yacy_search_server/DATA"]

# Start yacy ind debug mode (-d) to display console logs and to wait for yacy process
CMD sh /opt/yacy_search_server/startYACY.sh -d
