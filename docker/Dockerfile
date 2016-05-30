# Build a docker image from latest YaCy sources

# Base image : latest stable Debian 
FROM debian:latest

# Install needed packages
RUN apt-get update && apt-get install -yq \
	default-jdk \
	default-jre-headless \
	ant \
	git \
	curl \
	sudo
	
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

# Create user and group yacy : this user will be used to run YaCy main process
RUN adduser --system --group --no-create-home --disabled-password yacy

# Set ownership of yacy install directory to yacy user/group
RUN chown yacy:yacy -R /opt/yacy_search_server

# make some cleaning to reduce image size
RUN rm -rf .git \
	&& apt-get purge -yq --auto-remove \
		default-jdk \
		ant \
		git \ 
	&& apt-get clean

# Expose port 8090
EXPOSE 8090

# Set data volume : yacy data and configuration will persist aven after container stop or destruction
VOLUME ["/opt/yacy_search_server/DATA"]

# Start yacy as non-root process in debug mode (-d) to display console logs and to wait for yacy process
CMD sudo -u yacy sh /opt/yacy_search_server/startYACY.sh -d
