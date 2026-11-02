# Lightweight Java 21 JDK image
FROM eclipse-temurin:21-jdk-alpine

# Set application directory
WORKDIR /app

# Copy project files
COPY . .

# Compile all Java sources
RUN mkdir -p out && javac -d out src/medicircle/*.java

# Expose port (Render sets $PORT dynamically)
ENV PORT=8080
EXPOSE 8080

# Start WebServer
CMD ["java", "-cp", "out", "medicircle.WebServer"]
