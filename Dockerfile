# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency

# ---- run (Hugging Face Spaces: non-root uid 1000) ----
FROM eclipse-temurin:17-jre

RUN useradd -m -u 1000 user
USER user
WORKDIR /home/user/app

ENV PORT=8080
ENV VENDORYX_WEBAPP_DIR=/home/user/app/webapp
ENV VENDORYX_CLASSES_DIR=/home/user/app/classes

COPY --chown=user:user --from=build /app/target/classes /home/user/app/classes
COPY --chown=user:user --from=build /app/target/dependency /home/user/app/dependency
COPY --chown=user:user --from=build /app/src/main/webapp /home/user/app/webapp

EXPOSE 8080

CMD ["sh", "-c", "java -Xmx320m -XX:+UseSerialGC -cp '/home/user/app/classes:/home/user/app/dependency/*' com.vendoryx.AppServer"]
