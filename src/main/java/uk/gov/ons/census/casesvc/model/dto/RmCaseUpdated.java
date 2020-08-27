package uk.gov.ons.census.casesvc.model.dto;

import java.util.Optional;
import java.util.UUID;
import lombok.Data;

@Data
public class RmCaseUpdated {
  private UUID caseId;
  private String treatmentCode;
  private String estabType;
  private Optional<String> addressLine1;
  private Optional<String> addressLine2;
  private Optional<String> addressLine3;
  private Optional<String> organisationName;
  private Optional<String> townName;
  private Optional<String> postcode;
  private String oa;
  private String lsoa;
  private String msoa;
  private String lad;
  private String fieldCoordinatorId;
  private String fieldOfficerId;
  private Optional<Boolean> secureEstablishment;
  private Optional<Integer> ceExpectedCapacity;
  private Optional<String> uprn;
  private Optional<String> estabUprn;
  private Optional<String> abpCode;
  private String latitude;
  private String longitude;
  private Optional<String> htcWillingness;
  private Optional<String> htcDigital;
  private Optional<String> printBatch;
}
