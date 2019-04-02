package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class Address {
  private String addressLine1;
  private String addressLine2;
  private String addressLine3;
  private String townName;
  private String postcode;
  private String country;
  private String latitude;
  private String longitude;
  private String uprn;
  private String arid;
  private String addressType;
  private String estabType;
}
