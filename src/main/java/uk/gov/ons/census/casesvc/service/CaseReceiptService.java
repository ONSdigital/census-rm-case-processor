package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.mapQuestionnaireTypeToFormType;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Component
public class CaseReceiptService {

  private CaseService caseService;
  private final CaseRepository caseRepository;
  private static final String HH = "H";
  private static final String IND = "I";
  private static final String CE1 = "C";
  private static final String CONT = "Cont";
  private static final String EQ_EVENT_CHANNEL = "EQ";

  private Map<Key, BiFunction<Case, EventTypeDTO, Case>> rules = new HashMap<>();

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: https://collaborate2.ons.gov.uk/confluence/pages/viewpage.action?spaceKey=SDC&title=Receipting
    */

    rules.put(new Key("HH", "U", HH), receiptAndCancel);
    rules.put(new Key("HH", "U", IND), receiptAndCancel);
    rules.put(new Key("HH", "U", CE1), noActionRequired);
    rules.put(new Key("HH", "U", CONT), noActionRequired);
    rules.put(new Key("HI", "U", HH), receiptCase);
    rules.put(new Key("HI", "U", IND), receiptCase);
    rules.put(new Key("HI", "U", CE1), noActionRequired);
    rules.put(new Key("HI", "U", CONT), noActionRequired);
    rules.put(new Key("CE", "E", HH), incrementAndUpdate);
    rules.put(new Key("CE", "E", IND), incrementAndUpdate);
    rules.put(new Key("CE", "E", CE1), receiptAndUpdate);
    rules.put(new Key("CE", "E", CONT), noActionRequired);
    rules.put(new Key("CE", "U", HH), ceUnitRule);
    rules.put(new Key("CE", "U", IND), ceUnitRule);
    rules.put(new Key("CE", "U", CE1), noActionRequired);
    rules.put(new Key("CE", "U", CONT), noActionRequired);
    rules.put(new Key("SPG", "E", HH), noActionRequired);
    rules.put(new Key("SPG", "E", IND), noActionRequired);
    rules.put(new Key("SPG", "E", CE1), noActionRequired);
    rules.put(new Key("SPG", "E", CONT), noActionRequired);
    rules.put(new Key("SPG", "U", HH), receiptAndCancel);
    rules.put(new Key("SPG", "U", IND), noActionRequired);
    rules.put(new Key("SPG", "U", CE1), noActionRequired);
    rules.put(new Key("SPG", "U", CONT), noActionRequired);
  }

  public void receiptCase(UacQidLink uacQidLink, EventDTO causeEvent) {
    Case caze = uacQidLink.getCaze();

    if (uacQidLink.isBlankQuestionnaire() && !causeEvent.getChannel().equals(EQ_EVENT_CHANNEL)) {
      return;
    }

    Key ruleKey = makeRulesKey(caze, uacQidLink);

    if (!rules.containsKey(ruleKey)) {
      throw new RuntimeException(ruleKey.toString() + " does not map to any known processing rule");
    }

    rules.get(ruleKey).apply(caze, causeEvent.getType());
  }

  private Key makeRulesKey(Case caze, UacQidLink uacQidLink) {
    String formType = mapQuestionnaireTypeToFormType(uacQidLink.getQid());
    return new Key(caze.getCaseType(), caze.getAddressLevel(), formType);
  }

  private Case getCaseAndLockIt(UUID caseId) {
    return caseService.getCaseAndLockIt(caseId);
  }

  private Case incrementNoReceipt(Case caze) {
    Case lockedCase = getCaseAndLockIt(caze.getCaseId());
    lockedCase.setCeActualResponses(lockedCase.getCeActualResponses() + 1);

    return lockedCase;
  }

  private Case receiptCase(Case caze) {
    caze.setReceiptReceived(true);
    return caze;
  }

  BiFunction<Case, EventTypeDTO, Case> receiptAndCancel =
      (caze, causeEventType) -> {
        if (caze.isReceiptReceived()) {
          return caze;
        }
        Case updatedCase = receiptCase(caze);
        caseService.saveCaseAndEmitCaseUpdatedEvent(
            updatedCase, buildMetadata(causeEventType, ActionInstructionType.CANCEL));
        return caze;
      };

  BiFunction<Case, EventTypeDTO, Case> receiptAndUpdate =
      (caze, causeEventType) -> {
        if (caze.isReceiptReceived()) {
          return caze;
        }
        Case updatedCase = receiptCase(caze);
        caseService.saveCaseAndEmitCaseUpdatedEvent(
            updatedCase, buildMetadata(causeEventType, ActionInstructionType.UPDATE));
        return caze;
      };

  BiFunction<Case, EventTypeDTO, Case> receiptCase =
      (caze, causeEventType) -> {
        if (caze.isReceiptReceived()) {
          return caze;
        }
        Case updatedCase = receiptCase(caze);
        caseService.saveCaseAndEmitCaseUpdatedEvent(
            updatedCase, buildMetadata(causeEventType, null));
        return caze;
      };

  BiFunction<Case, EventTypeDTO, Case> incrementAndUpdate =
      (caze, causeEventType) -> {
        Case updatedCase = incrementNoReceipt(caze);
        caseService.saveCaseAndEmitCaseUpdatedEvent(
            updatedCase, buildMetadata(causeEventType, ActionInstructionType.UPDATE));
        return caze;
      };

  BiFunction<Case, EventTypeDTO, Case> ceUnitRule =
      (caze, causeEventType) -> {
        ActionInstructionType fieldInstruction = ActionInstructionType.UPDATE;

        Case lockedCase = incrementNoReceipt(caze);

        if (hasCeUnitResponsesMetExpected(lockedCase)) {
          lockedCase.setReceiptReceived(true);
          fieldInstruction = ActionInstructionType.CANCEL;
        }

        caseService.saveCaseAndEmitCaseUpdatedEvent(
            lockedCase, buildMetadata(causeEventType, fieldInstruction));
        return caze;
      };

  BiFunction<Case, EventTypeDTO, Case> noActionRequired = (caze, causeEventType) -> caze;

  @AllArgsConstructor
  @EqualsAndHashCode
  @ToString
  private class Key {

    private String caseType;
    private String addressLevel;
    private String formType;
  }

  private boolean hasCeUnitResponsesMetExpected(Case caze) {
    return caze.getCeExpectedCapacity() != null
        && !caze.isReceiptReceived()
        && caze.getCeActualResponses() >= caze.getCeExpectedCapacity();
  }
}
