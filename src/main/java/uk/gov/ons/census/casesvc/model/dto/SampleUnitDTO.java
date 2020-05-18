package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class SampleUnitDTO {
  private String addressType;
  private String estabType;
  private String addressLevel;
  private String organisationName;
  private String addressLine1;
  private String addressLine2;
  private String addressLine3;
  private String townName;
  private String postcode;
  private String latitude;
  private String longitude;
  private String fieldCoordinatorId;
  private String fieldOfficerId;
}
