package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class AddressModification {
  private CollectionCaseCaseId collectionCase;
  private Address originalAddress;
  private Address newAddress;
}
