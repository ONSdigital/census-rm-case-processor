# census-rm-case-processor 

[![Build Status](https://travis-ci.com/ONSdigital/census-rm-case-processor.svg?branch=master)](https://travis-ci.com/ONSdigital/census-rm-case-processor)

# Overview
Case Processor is implemented with Java 11 & Spring Integration, it is event driven listening and publishing to 
rabbitmq and persisting data to a SQL DB.

It is backed by the casesv2 schema and is responsible for maintaining all the data in that schema.
The separate case-api service provides a read-only api for this schema, it is a separate service by design to make sure
that api-calls do not block event processing or vice versa.

Case processor listens to a number of queues and acts on the messages by creating and updating cases as required
i.e. sample load, CCS case, Individual Response. 

When creating or updating a case this application will emit msgs to be consumed by RH, Field & the 
action-scheduler as appropriate.  Action-scheduler has a subset copy of the case data (CCS cases are excluded)

CCS cases are special cases which are emitted to field if listed without being supplied with a QID, unaddressed or refusal. 

Case service has a dependency on the census-rm-uac-qid-service, when case processor needs to create a new
UAC QID pair for a case it calls this service.

Case Processor also performs Event Logging against cases and uacquids.  These Events record the payload of Events 
received by it.


# Entrypoints / MessageEndpoints

There are multiple entry points to this application, these can be found in the messaging folder/package, each 
class in here is a listener to a queue (defined in the application yml).  These classes are annotated 
@MessageEndpoint and each consists of a receiveMessage function bound to a queue and marked @Transactional.  The 
 @Transactional part wraps every queuing & database action under this function into 1 big transaction.  If any of this 
fails they all get rolled back and the original message will be returned unharmed to the rabbit queue.  After several
failures the MessageException Service is configured to place a bad message onto a backoff queue.


# Testing

To test this service locally use:
   mvn clean install
   
This will run all of the unit tests, then if successful create a docker image for this application 
then bring up the required docker images from src/test/resources/docker-compose.yml (postgres, uacqidservice and rabbit)
to run the Integration Tests.

# Debug    
 If you want to debug the application/Integration tests start the required docker images by navigating 
 to src/test/resources/ and then  'docker-compose up'


# Configuration
By default the src/main/resources/application.yml is configured for 
[census-rm-docker-dev](https://github.com/ONSdigital/census-rm-docker-dev)

For production the configuration is overridden by the K8S apply script.

The queues are defined in src/test/resources/definitions.json for Integration Tests.
