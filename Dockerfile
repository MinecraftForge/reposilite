FROM eclipse-temurin:17-jdk as build-java
COPY reposilite-backend /root/project
COPY gradlew /root/project/gradlew
COPY gradle /root/project/gradle
WORKDIR /root/project
RUN chmod +x ./gradlew
RUN ./gradlew shadowJar --no-daemon

FROM openjdk:17-alpine

# Build-time metadata stage
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Reposilite" \
      org.label-schema.description="Lightweight repository management software dedicated for the Maven artifacts" \
      org.label-schema.url="https://reposilite.com/" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/MinecraftForge/reposilite" \
      org.label-schema.vendor="MinecraftForge" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"

# Run stage
RUN apk add --no-cache mailcap
WORKDIR /app
RUN mkdir -p /app/data
VOLUME /app/data
COPY --from=build-java /root/project/build/libs/reposilite*-all.jar reposilite.jar
ENTRYPOINT exec java $JAVA_OPTS -jar reposilite.jar -wd=/app/data $REPOSILITE_OPTS
