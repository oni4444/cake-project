FROM openjdk:17-jdk-slim

RUN apt update && apt install -y curl iproute2 net-tools


WORKDIR /app

COPY build/libs/cake-platform-0.0.1-SNAPSHOT.jar /app/cake-platform-0.0.1-SNAPSHOT.jar

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/cake-platform-0.0.1-SNAPSHOT.jar"]
CMD ["--spring.profiles.active=prod"]