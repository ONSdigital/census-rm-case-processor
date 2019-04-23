package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class Payload {
  CollectionCase collectionCase;
  Uac uac;
}
