package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.mapQuestionnaireTypeToFormType;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.iscontinuationQuestionnaireTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
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

  private Map<Key, Rule> rules = new HashMap<>();

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: https://collaborate2.ons.gov.uk/confluence/pages/viewpage.action?spaceKey=SDC&title=Receipting
    */

    // This exists to keep the rows on one line and readable
    ActionInstructionType FIELD_UPDATE = ActionInstructionType.UPDATE;

    rules.put(new Key("HH", "U", HH), new Rule(receiptCase, true, ActionInstructionType.CLOSE));
    rules.put(new Key("HH", "U", IND), new Rule(invalidMapping));
    rules.put(new Key("HH", "U", CE1), new Rule(invalidMapping));
    rules.put(new Key("HH", "U", CONT), new Rule(noActionRequired, false, null));
    rules.put(new Key("HI", "U", HH), new Rule(invalidMapping));
    rules.put(new Key("HI", "U", IND), new Rule(receiptCase, true, ActionInstructionType.NONE));
    rules.put(new Key("HI", "U", CE1), new Rule(invalidMapping));
    rules.put(new Key("HI", "U", CONT), new Rule(invalidMapping));
    rules.put(new Key("CE", "E", HH), new Rule(invalidMapping));
    rules.put(new Key("CE", "E", IND), new Rule(incremenNoReceipt, true, FIELD_UPDATE));
    rules.put(new Key("CE", "E", CE1), new Rule(receiptCase, true, ActionInstructionType.UPDATE));
    rules.put(new Key("CE", "E", CONT), new Rule(invalidMapping));
    rules.put(new Key("CE", "U", HH), new Rule(invalidMapping));
    rules.put(new Key("CE", "U", IND), new Rule(incrementAndReceipt, true, FIELD_UPDATE));
    rules.put(new Key("CE", "U", CE1), new Rule(invalidMapping));
    rules.put(new Key("CE", "U", CONT), new Rule(invalidMapping));
    rules.put(new Key("SPG", "E", HH), new Rule(noActionRequired, false, null));
    rules.put(new Key("SPG", "E", IND), new Rule(noActionRequired, false, null));
    rules.put(new Key("SPG", "E", CE1), new Rule(invalidMapping));
    rules.put(new Key("SPG", "E", CONT), new Rule(invalidMapping));
    rules.put(new Key("SPG", "U", HH), new Rule(receiptCase, true, ActionInstructionType.CLOSE));
    rules.put(new Key("SPG", "U", IND), new Rule(noActionRequired, false, null));
    rules.put(new Key("SPG", "U", CE1), new Rule(invalidMapping));
    rules.put(new Key("SPG", "U", CONT), new Rule(noActionRequired, false, null));
  }

  public void receiptCase(UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()) return;

    Key ruleKey = makeRulesKey(caze, uacQidLink);

    if (!rules.containsKey(ruleKey)) {
      throw new RuntimeException(ruleKey.toString() + " does not map to any known processing rule");
    }

    Rule rule = rules.get(ruleKey);
    Case lockedCase = rule.run(caze, uacQidLink);

    if (rule.getSaveAndEmitCase()) {
      caseService.saveCaseAndEmitCaseUpdatedEvent(
          lockedCase, buildMetadata(causeEventType, rule.getFieldInstruction()));
    }
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

  BiFunction<Case, UacQidLink, Case> incremenNoReceipt =
      (caze, uacQidLink) -> {
        Case lockedCase = getCaseAndLockIt(caze.getCaseId());
        lockedCase.setCeActualResponses(lockedCase.getCeActualResponses() + 1);

        return lockedCase;
      };

  BiFunction<Case, UacQidLink, Case> incrementAndReceipt =
      (caze, uacQidLink) -> {
        Case lockedCase = incremenNoReceipt.apply(caze, uacQidLink);

        if (lockedCase.getCeActualResponses() >= lockedCase.getCeExpectedCapacity()) {
          lockedCase.setReceiptReceived(true);
        }

        return lockedCase;
      };

  BiFunction<Case, UacQidLink, Case> receiptCase =
      (caze, uacQidLink) -> {
        caze.setReceiptReceived(true);
        return caze;
      };

  BiFunction<Case, UacQidLink, Case> noActionRequired = (caze, uacQidLink) -> caze;

  BiFunction<Case, UacQidLink, Case> invalidMapping =
      (caze, uacQidLink) -> {
        Key ruleKey = makeRulesKey(caze, uacQidLink);
        throw new RuntimeException(ruleKey.toString() + " is an invalid mapping");
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

  @AllArgsConstructor
  private class Rule {
    private BiFunction<Case, UacQidLink, Case> functionToExecute;
    private boolean saveAndEmitCase;
    private ActionInstructionType fieldInstruction;

    public Rule(BiFunction<Case, UacQidLink, Case> functionToExecute) {
      this.functionToExecute = functionToExecute;
    }

    public boolean getSaveAndEmitCase() {
      return saveAndEmitCase;
    }

    public ActionInstructionType getFieldInstruction() {
      return fieldInstruction;
    }

    public Case run(Case caze, UacQidLink uacQidLink) {
      return functionToExecute.apply(caze, uacQidLink);
    }
  }
}
