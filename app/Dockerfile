FROM eclipse-temurin:17 as jre-build

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

FROM gcr.io/distroless/base
ENV JAVA_HOME=/opt/java/openjdk
COPY --from=jre-build /javaruntime $JAVA_HOME

# Continue with your application deployment
#RUN mkdir /opt/app
COPY target/produsent-api/app.jar /opt/app/
COPY target/produsent-api/lib /opt/app/lib
CMD ["java", "$JAVA_OPTS", "-jar", "/opt/app/app.jar"]