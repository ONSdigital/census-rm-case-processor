package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isIndividualQuestionnaireType;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.iscontinuationQuestionnaireTypes;

import java.util.Optional;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Component
public class CaseReceiptService {
  private final CaseService caseService;
  private final CaseRepository caseRepository;

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
  }

  public void receiptCase(UacQidLink uacQidLink) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()) return;

    if (iscontinuationQuestionnaireTypes(uacQidLink.getQid())) return;

    if (caze.getCaseType().equals("CE") && isIndividualQuestionnaireType(uacQidLink.getQid())) {
      incrementActualResponseAndSetReceiptedIfAppropriate(caze);
      return;
    }

    caze.setReceiptReceived(true);
    caseService.saveAndEmitCaseUpdatedEvent(caze);
  }

  private void incrementActualResponseAndSetReceiptedIfAppropriate(Case caze) {
    /*
      This stops the actualResponses being updated by another thread for another receipt or linking event
    */
    Optional<Case> oCase = caseRepository.getCaseAndLockByCaseId(caze.getCaseId());

    oCase.ifPresentOrElse(
        lockedCase -> {
          lockedCase.setCeActualResponses(lockedCase.getCeActualResponses() + 1);

          if (lockedCase.getAddressLevel().equals("U")
              && lockedCase.getCeActualResponses().compareTo(lockedCase.getCeExpectedCapacity())
                  >= 0) {
            lockedCase.setReceiptReceived(true);
          }

          caseService.saveAndEmitCaseUpdatedEvent(lockedCase);
        },
        () -> {
          throw new RuntimeException(
              "Failed to get row to increment ceActualResponses, row is cccccckbbdbdccedgnbnuveiejurhedrdjlnfhjfjrkk"
                  + "probably locked and this should resolve: "
                  + caze.getCaseId());
        });
  }
}
