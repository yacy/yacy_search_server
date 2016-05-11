# Base image : latest stable Debian 
FROM debian:latest

# Install needed packages
RUN apt-get update && apt-get install -y \
	wget \
	default-jdk \
	ant
	
# Clean apt cache
RUN apt-get clean
	
# copy context : should be a YaCy git repository (remote or locally cloned)
# context can also be obtained from extracted sources archive, but version number will be default to 1.83/9000 when building
COPY . /opt/yacy_search_server

# trace content of copied directory
RUN ls /opt/yacy_search_server

# set current working dir to extracted sources directory
WORKDIR /opt/yacy_search_server
	
# Compile with ant
RUN ant clean compile

# clean .git directory useless now
RUN rm -rf .git

# Expose port 8090
EXPOSE 8090

# Set data volume : can be used to persist yacy data and configuration
VOLUME ["/opt/yacy_search_server/DATA"]

# Start yacy ind debug mode (-d) to display console logs and to wait for yacy process
CMD sh /opt/yacy_search_server/startYACY.sh -d
