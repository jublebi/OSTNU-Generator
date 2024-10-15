# hash:sha256:f3ebf79e73007e2095dd99744c5dd0367bdf2c6ec41fb84591fa5cce1fdcd97b
FROM registry.codeocean.com/codeocean/ubuntu-desktop:ubuntu-22.04-guacamole-1.5.2

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        maven \
    && rm -rf /var/lib/apt/lists/*

#Install java 21 manually
RUN apt install ca-certificates curl gnupg
RUN install -m 0755 -d /etc/apt/keyrings

RUN curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
RUN chmod a+r /etc/apt/keyrings/adoptium.gpg

RUN echo "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list

RUN apt update -y
RUN apt install -y temurin-21-jdk

# Try to load max of dependencies
COPY pom.xml /tmp
RUN cd /tmp && mvn dependency:resolve dependency:resolve-plugins dependency:go-offline