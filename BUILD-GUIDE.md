# ALA Profile Service Build Guide

This guide explains how to build **profile-service** (the Atlas of Living Australia Profiles back-end, a Grails application) on any machine: Linux, macOS (Intel or Apple Silicon), or Windows, on x86_64 or ARM64.

All paths in this guide are relative to the project root (the folder that contains `build.gradle`) unless stated otherwise. Replace placeholders such as `<GRADLE_INSTALL_DIR>` with locations of your choice.

---

## 1. What you are building

| Item | Value |
|------|-------|
| Framework | **Apache Grails 7.2.1** (Spring Boot 3.5, Groovy 4, Jakarta EE) |
| Build tool | Gradle **8.14.4** (`gradle/wrapper/gradle-wrapper.properties`) |
| Required JDK | **17** (`sourceCompatibility = '17'`). JDK 21 should also work for building and running, but 17 is the tested version. |
| Persistence | **MongoDB** (GORM for MongoDB) and **Elasticsearch 7.17** (search index). Both are needed at runtime, not at build time. |
| Authentication | ALA auth (OIDC/CAS via pac4j) and JWT for the web services |
| Version | `profileServiceVersion` in `gradle.properties` (e.g. `7.0.0-SNAPSHOT`) |
| Outputs | `build/libs/profile-service-<version>-exec.jar` (executable Spring Boot jar, the main artifact) and `build/libs/profile-service-<version>-plain.war` |

No Grails CLI is needed; everything is driven by Gradle.

---

## 2. Things to know before building

### 2.1 The Gradle wrapper jar is missing

`gradle/wrapper/gradle-wrapper.jar` is not in the repository, so `./gradlew` and `gradlew.bat` fail (`Could not find or load main class org.gradle.wrapper.GradleWrapperMain`). The same is true for `grailsw`. Install **Gradle 8.14.4** and run `gradle` directly (section 4).

Optional: with Gradle installed, `gradle wrapper --gradle-version 8.14.4` regenerates the wrapper.

### 2.2 Dependency repositories and SNAPSHOT libraries

The ALA security libraries are pinned to a **SNAPSHOT** version (`alaSecurityLibsVersion=8.0.0-SNAPSHOT` in `gradle.properties`) and are downloaded from the ALA Nexus. These repositories must be reachable from the build machine:

- `https://repo.grails.org/grails/core`
- `https://nexus.ala.org.au/content/groups/public/`
- `https://nexus.ala.org.au/content/repositories/snapshots`
- Maven Central and `https://plugins.gradle.org/m2/`

Because SNAPSHOT artifacts can be republished, two builds on different days may pick up different ALA library builds. To force Gradle to re-check them, add `--refresh-dependencies`.

### 2.3 `repo.grails.org` rate limiting (HTTP 429)

On a first build with an empty cache, Gradle downloads hundreds of artifacts in parallel and `repo.grails.org` may answer **`Received status code 429 from server: Too Many Requests`**. This is temporary. Re-run the build; to reduce the request rate, disable parallel execution for the first build:

```bash
gradle --no-daemon --no-parallel assemble
```

Already downloaded artifacts stay in the Gradle cache, so each retry gets further.

### 2.4 Optional "inplace" Elasticsearch plugin

`settings.gradle` and `build.gradle` support building against a local checkout of the Elasticsearch Grails plugin with `-Pinplace=true` (expects `../../elasticsearch-grails-plugin` relative to the project). This is only for plugin development. The default (`inplace=false` in `gradle.properties`) uses the published `org.grails.plugins:grails-elasticsearch:5.0.0` and needs nothing extra.

---

## 3. Install JDK 17

Any OpenJDK 17 build works (Eclipse Temurin, Azul Zulu, Amazon Corretto, Microsoft, Liberica, etc.), with native x86_64 and ARM64 builds including Apple Silicon.

| Platform | Command |
|----------|---------|
| SDKMAN (Linux, macOS, WSL) | `sdk install java 17.<x>-tem` (pick a version from `sdk list java`) |
| Debian / Ubuntu | `sudo apt install openjdk-17-jdk` |
| Fedora / RHEL | `sudo dnf install java-17-openjdk-devel` |
| Arch | `sudo pacman -S jdk17-openjdk` |
| macOS (Homebrew) | `brew install --cask temurin@17` |
| Windows (winget) | `winget install EclipseAdoptium.Temurin.17.JDK` |
| Windows (Chocolatey) | `choco install temurin17` |
| Manual | https://adoptium.net/temurin/releases/?version=17 |

Verify:

```bash
java -version    # must print version "17.x"
```

---

## 4. Install Gradle 8.14.4

| Option | Command |
|--------|---------|
| SDKMAN (Linux, macOS, WSL) | `sdk install gradle 8.14.4` |
| Manual (all platforms) | Download https://services.gradle.org/distributions/gradle-8.14.4-bin.zip and extract it to `<GRADLE_INSTALL_DIR>` |

Package managers may install Gradle 9, which has not been verified with this build and its Grails 7.2 Gradle plugins. Use 8.14.4 to match the project.

**Linux / macOS / WSL:**

```bash
export JAVA_HOME=<path-to-jdk17>
export PATH="$JAVA_HOME/bin:<GRADLE_INSTALL_DIR>/gradle-8.14.4/bin:$PATH"
```

**Windows PowerShell:**

```powershell
$env:JAVA_HOME = "<path-to-jdk17>"
$env:Path = "$env:JAVA_HOME\bin;<GRADLE_INSTALL_DIR>\gradle-8.14.4\bin;$env:Path"
```

**Windows cmd.exe:**

```bat
set JAVA_HOME=<path-to-jdk17>
set PATH=%JAVA_HOME%\bin;<GRADLE_INSTALL_DIR>\gradle-8.14.4\bin;%PATH%
```

Verify:

```bash
gradle -version    # Gradle 8.14.4, JVM: 17.x
```

---

## 5. Build

From the project root:

```bash
gradle --no-daemon assemble
# first build: add --no-parallel if repo.grails.org returns HTTP 429 (section 2.3)
```

The first build downloads all dependencies (cached in `~/.gradle`, `%USERPROFILE%\.gradle` on Windows) and takes a few minutes. Outputs in `build/libs/`:

| File | Description |
|------|-------------|
| `profile-service-<version>-exec.jar` | Executable Spring Boot jar (about 140 MB) with an embedded launch script. This is the artifact to deploy. |
| `profile-service-<version>-plain.war` | Plain WAR (the Spring Boot `bootWar` is disabled in `build.gradle`) |

Other useful commands:

```bash
gradle --no-daemon compileGroovy    # compile only
gradle --no-daemon clean            # delete build/
gradle --no-daemon check            # tests; need MongoDB and Elasticsearch, and browser drivers for Geb tests
```

`gradle publish` uploads the jar to GitHub Packages and needs `GITHUB_ACTOR` / `GITHUB_TOKEN`; it is not needed for a local build.

---

## 6. Build with Docker (no local JDK or Gradle)

Works on any OS and CPU that runs Docker (`eclipse-temurin:17-jdk` is multi-arch). From the project root:

```bash
docker run --rm -v "$PWD":/src -w /src eclipse-temurin:17-jdk bash -c '
  set -e
  apt-get update -qq && apt-get install -y -qq unzip curl >/dev/null
  curl -fsSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.14.4-bin.zip
  unzip -q /tmp/gradle.zip -d /opt
  /opt/gradle-8.14.4/bin/gradle --no-daemon --no-parallel assemble
'
```

On Windows PowerShell replace `"$PWD":/src` with `"${PWD}:/src"`. On Linux, files created by the container may be owned by `root`; remove them with `sudo` if needed. Add `-v gradle-cache:/root/.gradle` to keep the dependency cache between runs.

---

## 7. Run

### 7.1 Start MongoDB and Elasticsearch

The default configuration expects MongoDB on `localhost:27017` (database `profiles`) and Elasticsearch on `localhost:9200`. Quick start with Docker (both images are multi-arch, so they run natively on x86_64 and ARM64):

```bash
docker run -d --name profiles-mongo -p 27017:27017 mongo:6
docker run -d --name profiles-es -p 9200:9200 \
  -e discovery.type=single-node -e xpack.security.enabled=false \
  -e ES_JAVA_OPTS="-Xms512m -Xmx512m" \
  docker.elastic.co/elasticsearch/elasticsearch:7.17.21
```

On Linux hosts Elasticsearch may require `sudo sysctl -w vm.max_map_count=262144` if it exits on start-up.

### 7.2 Start the service

```bash
java -jar build/libs/profile-service-<version>-exec.jar --grails.serverURL=http://localhost:8080
```

Thanks to the embedded launch script, on Linux/macOS the jar can also be executed directly (`./build/libs/profile-service-<version>-exec.jar ...`).

`grails.serverURL` is mandatory: without it the ALA auth plugin fails at start-up with `Attribute absolute='true' specified but no grails.serverURL set in Config`. Set it to the public URL of the service.

Any configuration value can be overridden with `--key=value` arguments or environment variables, for example:

| Setting | Example |
|---------|---------|
| Server port | `--server.port=8081` |
| MongoDB host / port / database | `--grails.mongodb.host=db.example --grails.mongodb.port=27017 --grails.mongodb.databaseName=profiles` |
| Public URL | `--grails.serverURL=https://profiles-ws.example.org` |
| OIDC client | `--security.oidc.clientId=... --security.oidc.secret=...` |

The service reaches `https://auth.ala.org.au` for OIDC/JWT discovery, so outbound internet access is needed for authenticated requests.

### 7.3 External configuration file

Production ALA deployments put their settings in an external file. The application loads, if present:

- `/data/profile-service/config/profile-service-config.properties`
- `/data/profile-service/config/profile-service-config.yml`
- `/data/profile-service/config/profile-service-config.groovy`
- or the file named by the `PROFILE-SERVICE_CONFIG` environment variable or system property (see `grails-app/conf/application.groovy`). The name contains a hyphen, which most shells cannot export, so the system property is easier: `java -DPROFILE-SERVICE_CONFIG=<path-to-config-file> -jar ...`

On Windows, `/data/...` refers to `\data\...` on the current drive. Command-line arguments (section 7.2) work everywhere and are the simplest option for local runs.

### 7.4 Check that it is running

```bash
curl -i http://localhost:8080/          # 200, service home page
curl -i http://localhost:8080/opus      # 401: the API requires an ALA API key / JWT
```

---

## 8. Troubleshooting

| Symptom | Cause / Fix |
|---------|-------------|
| `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` | Wrapper jar missing (section 2.1). Use Gradle 8.14.4 directly. |
| `Received status code 429 from server: Too Many Requests` | `repo.grails.org` rate limiting (section 2.3). Re-run, with `--no-parallel`. |
| `Could not find au.org.ala:...:8.0.0-SNAPSHOT` / `org.grails.plugins:ala-auth:8.0.0-SNAPSHOT` | ALA Nexus not reachable or the snapshot was removed (section 2.2). Check access to `nexus.ala.org.au`. |
| `Unsupported class file major version` / `invalid source release: 17` | Wrong JDK. Use JDK 17. |
| `no grails.serverURL set in Config` at start-up | Pass `--grails.serverURL=...` (section 7.2). |
| `MongoTimeoutException` / `Connection refused` to 27017 | MongoDB not running or on another host/port (section 7.1). |
| Elasticsearch connection errors on start-up or search | Elasticsearch 7.17 not running on `localhost:9200`. |
| HTTP 401 on API endpoints | Expected without ALA credentials. |
| Out of memory while compiling | Increase `org.gradle.jvmargs` in `gradle.properties`. |

---

## 9. Quick reference

```bash
# Requirements: JDK 17 + Gradle 8.14.4 on PATH (sections 3 and 4)
java -version              # 17.x
gradle -version            # 8.14.4

# Build
gradle --no-daemon --no-parallel assemble     # -> build/libs/profile-service-<version>-exec.jar

# Run (needs MongoDB + Elasticsearch 7.17, see section 7.1)
java -jar build/libs/profile-service-<version>-exec.jar --grails.serverURL=http://localhost:8080
```
