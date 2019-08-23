package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class InvalidAddress {
  private InvalidAddressReason reason;
  private CollectionCaseCaseId collectionCase;
}
