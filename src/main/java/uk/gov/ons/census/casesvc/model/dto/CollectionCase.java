package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class CollectionCase {
  private String id;
  private String caseRef;
  private String caseType;
  private String survey;
  private String collectionExerciseId;
  private Address address;
  private String state;
  private OffsetDateTime actionableFrom;
  private Boolean receiptReceived;
  private Boolean refusalReceived;

  // Below this line is extra data potentially needed by Action Scheduler - can be ignored by RH
  private String actionPlanId;
  private String treatmentCode;
  private String oa;
  private String lsoa;
  private String msoa;
  private String lad;
  private String htcWillingness;
  private String htcDigital;
  private String fieldCoordinatorId;
  private String fieldOfficerId;
  private String ceExpectedCapacity;
  private Boolean addressInvalid;
  private Boolean undeliveredAsAddressed;
}
