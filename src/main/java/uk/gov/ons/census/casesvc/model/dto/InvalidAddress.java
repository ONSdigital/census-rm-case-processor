package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class InvalidAddress {
  private String reason;
  private CollectionCaseCaseId collectionCase;
}
