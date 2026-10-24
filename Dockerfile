# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency

# ---- run ----
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV PORT=8080
ENV VENDORYX_WEBAPP_DIR=/app/webapp
ENV VENDORYX_CLASSES_DIR=/app/classes

COPY --from=build /app/target/classes /app/classes
COPY --from=build /app/target/dependency /app/dependency
COPY --from=build /app/src/main/webapp /app/webapp

EXPOSE 8080

CMD ["sh", "-c", "java -Xmx320m -XX:+UseSerialGC -cp '/app/classes:/app/dependency/*' com.vendoryx.AppServer"]
