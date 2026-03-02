FROM openjdk:8-jdk-alpine

# Set working directory
WORKDIR /yacy

# Copy the project files
COPY . .

# Expose YaCy port
EXPOSE 8090

# Start YaCy
CMD ["sh", "startYACY.sh"]
