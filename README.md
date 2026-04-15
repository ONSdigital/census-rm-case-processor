# ssdc-rm-caseprocessor

Social (and Strategic) Survey Data Collection (SDC) Response Management (RM) Case Processor.

## Introduction

The case processor is responsible for managing a case, throughout its whole lifecycle: from sample load, to initial
contact letter, to reminder letter, to the end of the collection exercise.

The case processor has the following data model schema structure:

```text
  Survey
    └─ Collection Exercise
     ├─ Action Rule
     └─ Case
```

So, we have surveys, which have many collection exercises. A collection exercise has many action rules (e.g. initial
contact letter, reminder letter, outbound telephone, face-to-face fieldwork interview). A collection exercise has many
cases. Each case is created from a row in the sample file.

The case processor schedules action rules to be triggered at a specific date & time.

The case processor is responsible for creating the content of the CSV export file which will be used to export case data
for purposed such as printing letters, according to a flexible template. The export files can contain case refs, UACs,
QIDs and any attribute of the sample.

## Building
Podman and Docker are both supported for building and running the application.
By default the Makefile will use `docker` unless you are on an `arm64` architecture (e.g. M1/M2 Mac) in which case it will use `podman`.
You can override this by setting the `DOCKER` environment variable to either `docker` or `podman`.
For example, to force using `docker` on an M1/M2 Mac:
```shell
DOCKER=docker make <command>
```

To run all the tests and build the image

```shell
   make build
```

Just build the image

```shell
    make build-no-test
```

### Local Docker Java Healthcheck

Since docker compose health checks are run inside the container, we need a method of checking service health that can
run in our minimal alpine Java JRE images. To accomplish this, we have a small Java health check class which simply
calls a http endpoint and succeeds if it gets a success status. This is compiled into a JAR, which is then mounted into
the containers, so it can be executed by the JRE at container runtime.

#### Building Changes

If you make changes to the [HealthCheck.java](src/test/resources/java_healthcheck/HealthCheck.java), you must then
run `make rebuild-java-healthcheck` to compile and package the updated class into the jar, and commit the resulting
built changes.

## Debugging With PubSub Emulator

Make sure you have the following environment variables set if you want to run in the debugger in your IDE:

```shell
SPRING_CLOUD_GCP_PUBSUB_EMULATOR_HOST=localhost:8538
QUEUECONFIG_PUBSUB-PROJECT=our-project
```

## Debugging With GCP PubSub Project

If you want to use real GCP PubSub topics and subscriptions, make sure you have the following environment variables set
if you want to run in the debugger in your IDE:

```shell
SPRING_CLOUD_GCP_PUBSUB_PROJECT-ID=<GCP Project>
QUEUECONFIG_PUBSUB-PROJECT=<GCP Project>
```

### Enriched SMS Fulfilment

Topic: `rm-internal-sms-fulfilment_case-processor`

Example message:

```json
{
  "header": {
    "source": "TEST",
    "channel": "TEST",
    "topic": "rm-internal-sms-fulfilment_case-processor",
    "version": "v0.2_RELEASE",
    "dateTime": "2021-06-09T13:49:19.716761Z",
    "messageId": "92df974c-f03e-4519-8d55-05e9c0ecea43",
    "correlationId": "d6fc3b21-368e-43b5-baad-ef68d1a08629",
    "originatingUser": "dummy@example.com"
  },
  "payload": {
    "smsFulfilment": {
      "caseId": "2f2dc309-37cf-4749-85ea-ccb76ee69e4d",
      "packCode": "TEST_SMS_UAC",
      "uac": "RANDOMCHARS",
      "qid": "0123456789"
    }
  }
}
```

## Action Rules

The case processor triggers an action rule at its specified date & time. An action rule triggers once and only once. An
action rule applies to all cases for a collection exercise, according to a classifier, which allows certain cases to be
filtered out. Receipted, refused and invalid cases are always filtered out.

The SQL to create an action rule is as follows:

```sql
insert into casev3.action_rule
values ('f2af7113-eb93-4946-930d-3775e81a2666', -- action rule ID
        'left(sample ->> ''REGION'',1) = ''W''', -- classifier (e.g. only Wales region cases)
        'f', -- always false, meaning not triggered
        'REMINDER_ONE', -- pack code
        'PPO', -- export file destination name
        '["BOAT_NAME","MARINA_BERTH","__uac__"]', -- CSV file template
        '20210528T08:00:00.000', -- date/time to trigger acton rule
        'EXPORT_FILE', -- type of action rule
        '0184cb41-0529-40ff-a2b7-08770249b95c'); -- collection exercise ID
```

The action rule will only be triggered by the "leader" pod, in the event that multiple instances of the case processor
are running. If the leader dies or is killed/terminated, then another leader will be elected after 2 minutes.
