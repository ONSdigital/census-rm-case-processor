package uk.gov.ons.census.casesvc.service;

import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper;

@Component
public class EventProcessor {
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
    int questionnaireType =
        QuestionnaireTypeHelper.calculateQuestionnaireType(caze.getTreatmentCode());
    UacQidLink uacQidLink = uacProcessor.saveUacQidLink(caze, questionnaireType);
    uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
    caseProcessor.emitCaseCreatedEvent(caze);
    uacProcessor.logEvent(uacQidLink, CASE_CREATED_EVENT_DESCRIPTION, EventType.CASE_CREATED);
    uacProcessor.logEvent(uacQidLink, UAC_QID_LINKED_EVENT_DESCRIPTION, EventType.UAC_UPDATED);

    if (QuestionnaireTypeHelper.isQuestionnaireWelsh(caze.getTreatmentCode())) {
      uacQidLink = uacProcessor.saveUacQidLink(caze, 3);
      uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
      uacProcessor.logEvent(uacQidLink, UAC_QID_LINKED_EVENT_DESCRIPTION, EventType.UAC_UPDATED);
    }
  }
}
