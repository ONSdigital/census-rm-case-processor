package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Service
public class QuestionnaireLinkedService {
  private static final Logger log = LoggerFactory.getLogger(QuestionnaireLinkedService.class);
  private static final String QID_NOT_FOUND_ERROR = "Qid not found error";
  private static final String QUESTIONNAIRE_LINKED = "Questionnaire Linked";

  private final UacQidLinkRepository uacQidLinkRepository;
  private final CaseRepository caseRepository;
  private final UacService uacService;
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public QuestionnaireLinkedService(
      UacQidLinkRepository uacQidLinkRepository,
      CaseRepository caseRepository,
      UacService uacService,
      CaseService caseService,
      EventLogger eventLogger) {
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.caseRepository = caseRepository;
    this.uacService = uacService;
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processQuestionnaireLinked(ResponseManagementEvent questionnaireLinkedEvent) {
    UacDTO uac = questionnaireLinkedEvent.getPayload().getUac();
    Optional<UacQidLink> uacQidLinkOpt = uacQidLinkRepository.findByQid(uac.getQuestionnaireId());

    if (uacQidLinkOpt.isEmpty()) {
      log.error(QID_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Questionnaire Id '%s' not found!", uac.getQuestionnaireId()));
    }

    UacQidLink uacQidLink = uacQidLinkOpt.get();

    Case caze = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));

    // If UAC/QID has been receipted before case, update case
    if (!uacQidLink.isActive() && !caze.isReceiptReceived()) {
      caze.setReceiptReceived(true);
      caseRepository.saveAndFlush(caze);
      caseService.emitCaseUpdatedEvent(caze);
    }

    uacQidLink.setCaze(caze);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    uacService.emitUacUpdatedEvent(uacQidLink, caze);

    eventLogger.logUacQidEvent(
        uacQidLink,
        questionnaireLinkedEvent.getEvent().getDateTime(),
        OffsetDateTime.now(),
        QUESTIONNAIRE_LINKED,
        EventType.QUESTIONNAIRE_LINKED,
        questionnaireLinkedEvent.getEvent(),
        convertObjectToJson(uac));
  }
}
