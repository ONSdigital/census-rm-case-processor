package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class NewAddress {
  String sourceCaseId;
  CollectionCase collectionCase;
}
