FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp

# Installation de Tesseract OCR
#RUN apk --no-cache add tesseract-ocr

COPY target/*.jar app.jar

# Copier le dossier tessdata depuis les resources vers l'image
#COPY src/main/resources/tessdata /app/tessdata

#ENV TESSDATA_PREFIX /app/tessdata

ENTRYPOINT ["java", "-jar", "/app.jar"]
EXPOSE 8080
