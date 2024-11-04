## Purpose
This is a personal project I created, licensed under the MIT license, to assist Java development teams in securing their code with obfuscators without having to include the obfuscation library and configuration in each project.  
There are two parts to this project:
1. The Server Backend (written using express) (`ros-express-backend`)
    - This backend handles obfuscation requests via its API, using GitHub PATs for authorization
    - See [Backend-Deployment](https://github.com/Jake-Moore/remote-obfuscation-server/blob/main/.docs/Backend-Deployment.md) for details on setting this up
    - See [User-Authorization](https://github.com/Jake-Moore/remote-obfuscation-server/blob/main/.docs/User-Authorization.md) for details on how users authenticate
2. The Gradle Plugin (`ros-gradle-plugin`)
    - This simple gradle plugin connects with the backend server to automatically obfuscate the build jars
    - See [Gradle-Plugin-Usage](https://github.com/Jake-Moore/remote-obfuscation-server/blob/main/.docs/Gradle-Plugin-Usage.md) for details on using the gradle plugin

## Motivation
This layout was created so that the individual obfuscation strategy, application, and physical files were invisible to individual developers.  The obfuscator can be installed on the backend without needing copies of it placed in each project's build. Additionally, the gradle plugin simplifies the process of adding complicated obfuscation into each project. Config files used by the obfuscator can be passed along side the input jar in the obfuscation api request, allowing seemless configuration.

## Documentation
See the [https://github.com/Jake-Moore/remote-obfuscation-server/tree/main/.docs](documentation) for information on setting up this project.
