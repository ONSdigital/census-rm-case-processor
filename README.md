# census-rm-case-processor
Case Service using latest versions of Java, Spring Integration and using DSL instead of XML for configuration.

# How to run
The new Case Service Processor requires Postgres, Rabbit and the IAC service to be running - you can use census-rm-docker-dev to start these dependencies.

To run in development mode, make sure you specify the following in VM Options:
```
-Dspring.profiles.active=dev
```