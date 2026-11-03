FROM maven:3-eclipse-temurin-25-alpine AS deps-build
WORKDIR /deps
RUN apk add --no-cache git unzip

# 1. Build llm-access under a unique version
RUN git clone --depth 1 --branch feature/make-env-variables-optional \
    https://github.com/ardoco/llm-access.git llm-access
WORKDIR /deps/llm-access
RUN mvn -B versions:set -DnewVersion=0.1.0-LOCAL -DgenerateBackupPoms=false
RUN mvn -B -DskipTests -Dspotless.check.skip=true install
RUN unzip -l /root/.m2/repository/io/github/ardoco/llm-access/0.1.0-LOCAL/llm-access-0.1.0-LOCAL.jar | grep -E "EnvironmentProvider|SystemEnvironment"

# 2. Point lissa at it
WORKDIR /deps
RUN git clone --depth 1 --branch feature/llm-lib-custom-env \
    https://github.com/ardoco/lissa.git lissa
WORKDIR /deps/lissa
RUN mvn -B versions:use-dep-version -Dincludes=io.github.ardoco:llm-access \
    -DdepVersion=0.1.0-LOCAL -DforceVersion=true -DgenerateBackupPoms=false
RUN mvn -B versions:set -DnewVersion=0.2.2-SNAPSHOT -DgenerateBackupPoms=false
RUN mvn -B -DskipTests -Dspotless.check.skip=true install

FROM maven:3-eclipse-temurin-25-alpine AS build
WORKDIR /build
# Copy the whole local repo, since llm-access may live under a different groupId than lissa
COPY --from=deps-build /root/.m2/repository /root/.m2/repository
COPY . .
RUN mvn -DskipTests clean package

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache bash
COPY --from=build /build/target/rest-*.jar /app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT java -jar /app.jar --server.use-forward-headers=true --server.forward-headers-strategy=framework
