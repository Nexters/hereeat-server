# Fallback build path when Jib is unavailable.
FROM amazoncorretto:25-alpine-jdk
ENV TZ=Asia/Seoul
ARG JAR_FILE=apps/api/build/libs/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
