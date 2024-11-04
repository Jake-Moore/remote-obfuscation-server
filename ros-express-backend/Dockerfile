# Use a lightweight Linux image with OpenJDK
FROM openjdk:17-jdk-slim

# Install Node.js and npm
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# Set the working directory
WORKDIR /home/container

# Copy only package files
COPY package.json package-lock.json* ./

# Install dependencies
RUN npm install

# Copy the rest of your application code
COPY . .

# Use the node deploy script
CMD ["npm", "run", "deploy"]
