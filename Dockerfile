# Base image : latest stable Debian 
FROM debian:latest

# Install needed packages
RUN apt-get update && apt-get install -yq \
	default-jdk \
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

# make some cleaning to reduce image size
RUN rm -rf .git && apt-get clean

# Expose port 8090
EXPOSE 8090

# Set data volume : can be used to persist yacy data and configuration
VOLUME ["/opt/yacy_search_server/DATA"]

# Start yacy ind debug mode (-d) to display console logs and to wait for yacy process
CMD sh /opt/yacy_search_server/startYACY.sh -d
