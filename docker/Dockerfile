FROM amazoncorretto:25-alpine-jdk
ENV TZ=Asia/Seoul
ARG JAR_FILE=apps/api/build/libs/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "/app.jar"]
