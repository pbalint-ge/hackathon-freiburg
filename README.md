## hackathon-freiburg

This repository contains a java webapplication demoing an example usage of the HAPI FHIR client library:

https://hapifhir.io/

https://hapifhir.io/hapi-fhir/docs/client/introduction.html

It's a plain spring boot application built using maven, exposing 2 endpoints, one displaying **appointment** data in a tablular format, while the other returns the same data as json:

http://localhost:8080/test/page?startDateTime=2018-01-01T01:02:03&endDateTime=2018-01-02T03:02:01&modality=CT

http://localhost:8080/test/response?startDateTime=2018-01-01T01:02:03&endDateTime=2018-01-02T03:02:01&modality=CT

# Before running
Check the application.yml and verify if you've configured your proxy (if any) and the url and api keys are correct

# Notes
Data is parsed from the HAPI FHIR data structures to custom ones (in AppointmentRetrievalService) as an example, so only a few data fields are copied, missing out resources like the practitioner!

To quickly run it using maven, issue the following commands:
```
mvn package
mvn spring-boot:run
```
Make sure to also check the .pptx!
