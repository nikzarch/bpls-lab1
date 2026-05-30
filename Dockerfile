FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x ./gradlew

COPY src src

RUN ./gradlew clean bootWar -x test

FROM tomcat:10.1-jdk21-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build /app/build/libs/labpay.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=api
ENV APP_JMS_URL=amqp://admin:admin@akarpov.ru:5672

CMD ["catalina.sh", "run"]