package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.mapQuestionnaireTypeToFormType;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.iscontinuationQuestionnaireTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Component
public class CaseReceiptService {
  private final CaseService caseService;
  private final CaseRepository caseRepository;
  private static final String HH = "H";
  private static final String IND = "I";
  private static final String CE1 = "C";
  private static final String CONT = "Cont";

  private Map<Key, IRule> rules = new HashMap<>();

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: https://collaborate2.ons.gov.uk/confluence/pages/viewpage.action?spaceKey=SDC&title=Receipting
    */

    rules.put(new Key("HH", "U", HH), new Rule(receiptCase, ActionInstructionType.CLOSE));
    rules.put(new Key("HH", "U", CONT), new NoActionRequired());
    rules.put(new Key("HI", "U", IND), new Rule(receiptCase, null));
    rules.put(new Key("CE", "E", IND), new Rule(incremenNoReceipt, ActionInstructionType.UPDATE));
    rules.put(new Key("CE", "E", CE1), new Rule(receiptCase, ActionInstructionType.UPDATE));
    rules.put(new Key("CE", "U", IND), new Rule(incrementAndReceipt, ActionInstructionType.UPDATE));
    rules.put(new Key("SPG", "E", HH), new NoActionRequired());
    rules.put(new Key("SPG", "E", IND), new NoActionRequired());
    rules.put(new Key("SPG", "U", HH), new Rule(receiptCase, ActionInstructionType.CLOSE));
    rules.put(new Key("SPG", "U", IND), new NoActionRequired());
    rules.put(new Key("SPG", "U", CONT), new NoActionRequired());
  }

  public void receiptCase(UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()) return;

    Key ruleKey = makeRulesKey(caze, uacQidLink);

    if (!rules.containsKey(ruleKey)) {
      throw new RuntimeException(ruleKey.toString() + " does not map to any known processing rule");
    }

    IRule rule = rules.get(ruleKey);
    rule.run(caze, causeEventType);
  }

  private Key makeRulesKey(Case caze, UacQidLink uacQidLink) {
    String formType;

    if (iscontinuationQuestionnaireTypes(uacQidLink.getQid())) {
      formType = "Cont";
    } else {
      formType = mapQuestionnaireTypeToFormType(uacQidLink.getQid());
    }

    return new Key(caze.getCaseType(), caze.getAddressLevel(), formType);
  }

  Function<Case, Case> incremenNoReceipt =
      (caze) -> {
        Case lockedCase = getCaseAndLockIt(caze.getCaseId());
        lockedCase.setCeActualResponses(lockedCase.getCeActualResponses() + 1);

        return lockedCase;
      };

  Function<Case, Case> incrementAndReceipt =
      (caze) -> {
        Case lockedCase = incremenNoReceipt.apply(caze);

        if (lockedCase.getCeActualResponses() >= lockedCase.getCeExpectedCapacity()) {
          lockedCase.setReceiptReceived(true);
        }

        return lockedCase;
      };

  Function<Case, Case> receiptCase =
      (caze) -> {
        caze.setReceiptReceived(true);
        return caze;
      };

  private Case getCaseAndLockIt(UUID caseId) {
    Optional<Case> oCase = caseRepository.getCaseAndLockByCaseId(caseId);

    if (!oCase.isPresent()) {
      throw new RuntimeException(
          "Failed to get row to increment responses, row is probably locked and this should resolve itself: "
              + caseId);
    }

    return oCase.get();
  }

  @AllArgsConstructor
  @EqualsAndHashCode
  @ToString
  private class Key {
    private String caseType;
    private String addressLevel;
    private String formType;
  }

  private interface IRule {
    void run(Case caze, EventTypeDTO causeEventType);
  }

  @AllArgsConstructor
  private class Rule implements IRule {
    private Function<Case, Case> functionToExecute;
    private ActionInstructionType fieldInstruction;

    public void run(Case caze, EventTypeDTO causeEventType) {
      Case lockedCase = functionToExecute.apply(caze);
      caseService.saveCaseAndEmitCaseUpdatedEvent(
          lockedCase, buildMetadata(causeEventType, fieldInstruction));
    }
  }

  private class NoActionRequired implements IRule {

    @Override
    public void run(Case caze, EventTypeDTO causeEventType) {
      // No Action
    }
  }
}
