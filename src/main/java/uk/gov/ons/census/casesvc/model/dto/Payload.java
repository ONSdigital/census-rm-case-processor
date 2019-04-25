package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class Payload {
  private CollectionCase collectionCase;
  private Uac uac;
}
