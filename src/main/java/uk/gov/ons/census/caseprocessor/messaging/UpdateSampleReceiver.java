package uk.gov.ons.census.caseprocessor.messaging;

import static java.util.function.Predicate.not;
import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UpdateSample;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.caseprocessor.utils.SampleValidateHelper;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.validation.ColumnValidator;

@MessageEndpoint
public class UpdateSampleReceiver {

  private final CaseService caseService;
  private final EventLogger eventLogger;

  public UpdateSampleReceiver(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @ServiceActivator(inputChannel = "updateSampleInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());

    UpdateSample updateSample = event.getPayload().getUpdateSample();

    Case caze = caseService.getCaseAndLockForUpdate(updateSample.getCaseId());

    List<String> validationErrors = new ArrayList<>();
    for (Map.Entry<String, String> entry : updateSample.getSample().entrySet()) {
      String columnName = entry.getKey();
      String newValue = entry.getValue();

      validateOnlyDefinedSampleDataBeingUpdated(caze, columnName);

      // Validate the updated value according to the rules for the column
      for (ColumnValidator columnValidator :
          caze.getCollectionExercise().getSurvey().getSampleValidationRules()) {

        SampleValidateHelper.validateNewValue(columnName, newValue, columnValidator)
            .ifPresent(validationErrors::add);
      }

      caze.getSample().put(entry.getKey(), entry.getValue());
    }

    if (!validationErrors.isEmpty()) {
      throw new RuntimeException(
          EventType.UPDATE_SAMPLE
              + " event: "
              + validationErrors.stream().collect(Collectors.joining(System.lineSeparator())));
    }

    caseService.saveCaseAndEmitCaseUpdate(
        caze, event.getHeader().getCorrelationId(), event.getHeader().getOriginatingUser());

    eventLogger.logCaseEvent(caze, "Sample data updated", EventType.UPDATE_SAMPLE, event, message);
  }

  private void validateOnlyDefinedSampleDataBeingUpdated(Case caze, String columnName) {
    if (Arrays.stream(caze.getCollectionExercise().getSurvey().getSampleValidationRules())
        .filter(not(ColumnValidator::isSensitive))
        .filter(columnValidator -> columnValidator.getColumnName().equals(columnName))
        .findFirst()
        .isEmpty()) {
      throw new RuntimeException("Column name '" + columnName + "' is not within defined sample");
    }
  }
}
