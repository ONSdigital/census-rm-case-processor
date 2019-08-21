package uk.gov.ons.census.casesvc.model.dto;

public enum InvalidAddressReason {
  DERELICT,
  DEMOLISHED,
  CANT_FIND,
  UNADDRESSABLE_OBJECT,
  NON_RESIDENTIAL,
  DUPLICATE,
  UNDER_CONSTRUCTION,
  SPLIT_ADDRESS
}
