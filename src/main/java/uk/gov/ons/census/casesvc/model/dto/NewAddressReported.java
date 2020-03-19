package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class NewAddressReported {
  UUID sourceCaseID;
  CollectionCase collectionCase;
}
