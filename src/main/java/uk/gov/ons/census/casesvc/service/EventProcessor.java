package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Component
public class EventProcessor {
  private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

  private static final String UNKNOWN_COUNTRY_ERROR = "Unknown Country";
  private static final String UNEXPECTED_CASE_TYPE_ERROR = "Unexpected Case Type";

  private static final String CASE_CREATED_EVENT_DESCRIPTION = "Case created";
  private static final String UAC_QID_LINKED_EVENT_DESCRIPTION = "UAC QID linked";

  private final CaseProcessor caseProcessor;
  private final UacProcessor uacProcessor;

  public EventProcessor(CaseProcessor caseProcessor, UacProcessor uacProcessor) {
    this.caseProcessor = caseProcessor;
    this.uacProcessor = uacProcessor;
  }

  public void processSampleReceivedMessage(CreateCaseSample createCaseSample) {
    Case caze = caseProcessor.saveCase(createCaseSample);
    int questionnaireType = calculateQuestionnaireType(caze.getTreatmentCode());
    UacQidLink uacQidLink = uacProcessor.saveUacQidLink(caze, questionnaireType);
    uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
    caseProcessor.emitCaseCreatedEvent(caze);
    uacProcessor.logEvent(uacQidLink, CASE_CREATED_EVENT_DESCRIPTION);
    uacProcessor.logEvent(uacQidLink, UAC_QID_LINKED_EVENT_DESCRIPTION);

    if (isQuestionnaireWelsh(caze.getTreatmentCode())) {
      uacQidLink = uacProcessor.saveUacQidLink(caze, 3);
      uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
      uacProcessor.logEvent(uacQidLink, UAC_QID_LINKED_EVENT_DESCRIPTION);
    }
  }

  private boolean isQuestionnaireWelsh(String treatmentCode) {
    return (treatmentCode.startsWith("HH_Q") && treatmentCode.endsWith("W"));
  }

  private int calculateQuestionnaireType(String treatmentCode) {
    String country = treatmentCode.substring(treatmentCode.length() - 1);
    if (!country.equals("E") && !country.equals("W") && !country.equals("N")) {
      log.with("treatment_code", treatmentCode).error(UNKNOWN_COUNTRY_ERROR);
      throw new IllegalArgumentException();
    }

    if (treatmentCode.startsWith("HH")) {
      switch (country) {
        case "E":
          return 1;
        case "W":
          return 2;
        case "N":
          return 4;
      }
    } else if (treatmentCode.startsWith("CI")) {
      switch (country) {
        case "E":
          return 21;
        case "W":
          return 22;
        case "N":
          return 24;
      }
    } else if (treatmentCode.startsWith("CE")) {
      switch (country) {
        case "E":
          return 31;
        case "W":
          return 32;
        case "N":
          return 34;
      }
    } else {
      log.with("treatment_code", treatmentCode).error(UNEXPECTED_CASE_TYPE_ERROR);
      throw new IllegalArgumentException();
    }

    throw new RuntimeException(); // This code should be unreachable
  }
}
