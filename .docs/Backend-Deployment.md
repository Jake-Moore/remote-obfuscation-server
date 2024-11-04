## Remote Obfuscation Server - Deployment

### Enviornment Variables
**Required:**
- `ROS_REQUIRED_ORGS`
  - The git organizations a user must be a part of to access the obfuscation server
  - Use a comma-delimited list of org names, i.e. `ROS_REQUIRED_ORGS=MyOrg1,MyOrg2`
- `ROS_OBFUSCATOR_TYPE`
  - The obfuscator name you are planning to use. 
  - Options: `ALLATORI`
    - feel free to make a PR with more implementations!

**Optional:**
- `ROS_PORT`
  - default port 8000
  - Use to override the exposed express port
- `ROS_OBFUSCATOR_PATH`
  - default '/obfuscator/myObfuscator.jar'
  - Use for custom obfuscator file locations
- `ROS_LOG_STORAGE`
  - default `/var/log/ros`
  - Where obfuscation logs should be stored
- `ROS_REVERSE_PROXY`
  - default 'false'
  - Set to true when ROS is behind a reverse proxy, to resolve client ips from the proxy
  - More specifically, this enables the `trust proxy` flag in Express, relying on the `X-Forwarded-` headers to resolve ips
    - See https://expressjs.com/en/guide/behind-proxies.html for more details

### Sample docker-compose.yml
```yml
services:
  remote-obfuscation-server:
    image: ghcr.io/jake-moore/remote-obfuscation-server:latest
    ports:
      - "8000:8000" # Default port 8000
    volumes:
      # Persistent log storage
      - /your/logs/location/ros:/var/log/ros # CHANGEME
      # Grant ROS access to your obfuscator
      - /your/obfuscator/location/myObfuscator.jar:/obfuscator/myObfuscator.jar # CHANGEME
    environment:
      ROS_REQUIRED_ORGS: '' # CHANGEME
      # ROS_REQUIRED_ORGS: 'MyOrganizationName' # Example of one org
      # ROS_REQUIRED_ORGS: 'MyOrganizationName,Google' # Example of multiple orgs

```

### HTTPS
HTTPS is **not directly supported** by remote-obfuscation-server.  
The Docker image is running an express app that exposes only on an HTTP port.  
It is recommended that you deploy a **reverse-proxy** (like nginx) to add HTTPS