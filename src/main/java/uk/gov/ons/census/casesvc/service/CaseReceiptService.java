package uk.gov.ons.census.casesvc.service;

import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

import java.util.Optional;

import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isIndividualQuestionnaireType;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.iscontinuationQuestionnaireTypes;

@Component
public class CaseReceiptService {
  private final CaseService caseService;
  private final CaseRepository caseRepository;

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
  }

  public void receiptCase(UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()) return;

    if (iscontinuationQuestionnaireTypes(uacQidLink.getQid())) return;

    if (caze.getCaseType().equals("CE") && isIndividualQuestionnaireType(uacQidLink.getQid())) {
      incrementActualResponseAndSetReceiptedIfAppropriate(caze, causeEventType);
      return;
    }

    caze.setReceiptReceived(true);
    caseService.saveCaseAndEmitCaseUpdatedEvent(
        caze, buildMetadata(causeEventType, ActionInstructionType.CLOSE));
  }

  private void incrementActualResponseAndSetReceiptedIfAppropriate(
      Case caze, EventTypeDTO causeEventType) {
    /*
      This stops the actualResponses being updated by another thread for another receipt or linking event
    */
    Optional<Case> oCase = caseRepository.getCaseAndLockByCaseId(caze.getCaseId());

    if (!oCase.isPresent()) {
      throw new RuntimeException(
          "Failed to get row to increment responses, row is probably locked and this should resolve itself: "
              + caze.getCaseId());
    }

    Case lockedCase = oCase.get();
    lockedCase.setCeActualResponses(lockedCase.getCeActualResponses() + 1);

    if (lockedCase.getAddressLevel().equals("U")
        && lockedCase.getCeActualResponses().intValue()
            >= lockedCase.getCeExpectedCapacity().intValue()) {
      lockedCase.setReceiptReceived(true);
    }

    caseService.saveCaseAndEmitCaseUpdatedEvent(
        lockedCase, buildMetadata(causeEventType, ActionInstructionType.CLOSE));
  }
}
