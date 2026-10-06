# syntax=docker/dockerfile:1
# Imagen de la API: se compila con el JDK y se ejecuta con el JRE, por capas (las dependencias cambian poco y se
# reutilizan entre versiones) y sin privilegios.

FROM eclipse-temurin:25-jdk-noble AS construccion
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true \
    && java -Djarmode=tools -jar target/radar-api-*.jar extract --layers --launcher --destination extraido

FROM eclipse-temurin:25-jre-noble
RUN groupadd --system radar && useradd --system --gid radar --home-dir /app radar \
    && mkdir -p /datos/descargas && chown radar:radar /datos/descargas
WORKDIR /app
COPY --from=construccion /src/extraido/dependencies/ ./
COPY --from=construccion /src/extraido/spring-boot-loader/ ./
COPY --from=construccion /src/extraido/snapshot-dependencies/ ./
COPY --from=construccion /src/extraido/application/ ./
USER radar
ENV RADAR_DESCARGAS=/datos/descargas
EXPOSE 8080
# Sin curl en la imagen: la comprobación de salud usa bash y /dev/tcp.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health/readiness HTTP/1.0\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
