package uk.gov.ons.census.caseprocessor.messaging;

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
import uk.gov.ons.census.caseprocessor.model.dto.UpdateSampleSensitive;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.caseprocessor.utils.SampleValidateHelper;
import uk.gov.ons.census.common.model.entity.*;
import uk.gov.ons.census.common.validation.ColumnValidator;

@MessageEndpoint
public class UpdateSampleSensitiveReceiver {
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public UpdateSampleSensitiveReceiver(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @ServiceActivator(inputChannel = "updateSampleSensitiveInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());

    UpdateSampleSensitive updateSampleSensitive = event.getPayload().getUpdateSampleSensitive();

    Case caze = caseService.getCaseAndLockForUpdate(updateSampleSensitive.getCaseId());

    List<String> validationErrors = new ArrayList<>();

    for (Map.Entry<String, String> entry : updateSampleSensitive.getSampleSensitive().entrySet()) {
      String columnName = entry.getKey();
      String newValue = entry.getValue();

      validateUpdateWithinSensitiveSampleDefinition(caze, columnName);

      // Blanking out the sensitive PII data is allowed, for GDPR reasons
      if (newValue.length() != 0) {

        // If the data is not being blanked, validate it according to rules
        for (ColumnValidator columnValidator :
            caze.getCollectionExercise().getSurvey().getSampleValidationRules()) {

          SampleValidateHelper.validateNewValue(columnName, newValue, columnValidator)
              .ifPresent(validationErrors::add);
        }
      }

      // Finally, update the cases sample sensitive blob with the validated value
      caze.getSampleSensitive().put(columnName, newValue);
    }

    if (!validationErrors.isEmpty()) {
      throw new RuntimeException(
          EventType.UPDATE_SAMPLE_SENSITIVE
              + " event: "
              + validationErrors.stream().collect(Collectors.joining(System.lineSeparator())));
    }

    caseService.saveCaseAndEmitCaseUpdate(
        caze, event.getHeader().getCorrelationId(), event.getHeader().getOriginatingUser());

    eventLogger.logCaseEvent(
        caze, "Sensitive data updated", EventType.UPDATE_SAMPLE_SENSITIVE, event, message);
  }

  private void validateUpdateWithinSensitiveSampleDefinition(Case caze, String columnName) {
    if (Arrays.stream(caze.getCollectionExercise().getSurvey().getSampleValidationRules())
        .filter(ColumnValidator::isSensitive)
        .filter(columnValidator -> columnValidator.getColumnName().equals(columnName))
        .findFirst()
        .isEmpty()) {
      throw new RuntimeException(
          "Column name '" + columnName + "' is not within defined sensitive sample");
    }
  }
}
