FROM artifacts.openid.co.za/odin-docker-shared-local/capiteccorretto:21-alpine-jdk

ARG JAR_FILE=application-1.0.0-SNAPSHOT.jar
ENV JAR_FILE=$JAR_FILE

COPY target/$JAR_FILE /
COPY certs/* /etc/ssl/certs/

RUN pushd /etc/ssl/certs; for file in *.pem; do keytool -cacerts -importcert -alias ${file%%.pem} -file $file -storepass changeit -noprompt; done; popd; rm -rf /tmp/*

USER 1000
EXPOSE 8080
ENV TZ="Africa/Johannesburg"

ENTRYPOINT exec java $JAVA_OPTS -jar /$JAR_FILE
