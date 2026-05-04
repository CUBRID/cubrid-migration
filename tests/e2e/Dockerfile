FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

# Cache Maven dependencies (re-downloaded only when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy test source code and resources
COPY src/ src/
COPY e2e-test.properties .

CMD ["mvn", "test", \
     "-Dpicocli.ansi=false", \
     "-Dorg.fusesource.jansi.Ansi.disable=true"]
