package uk.gov.ons.census.casesvc.service;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;

@Component
public class InvalidAddressService {
  private final CaseProcessor caseProcessor;

  public InvalidAddressService(CaseProcessor caseProcessor) {
    this.caseProcessor = caseProcessor;
  }

  public void processMessage(ResponseManagementEvent invalidAddressEvent) {
//    Case caze =
//        caseProcessor.getCaseByCaseId(
//            UUID.fromString(
//                invalidAddressEvent.getPayload().getInvalidAddress().getCollectionCase().getId()));
//    caze.setAddressInvalid(true);
  }
}
