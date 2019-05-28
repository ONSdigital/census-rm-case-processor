# census-rm-case-processor

[![Build Status](https://travis-ci.com/ONSdigital/census-rm-case-processor.svg?branch=master)](https://travis-ci.com/ONSdigital/census-rm-case-processor)


Case Processor is rewritten Case Service using latest versions of Java, Spring Integration and using DSL instead of XML for configuration. Also separates out the RESTful API into another project (i.e. Case API).

# How to run
The new Case Processor requires Postgres, Rabbit and the IAC service to be running - you can use census-rm-docker-dev to start these dependencies.

To run in development mode, make sure you specify the following in VM Options:
```
-Dspring.profiles.active=dev
```
