package uk.gov.ons.census.casesvc.model.entity;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "cases")
public class Case {
  @Id private UUID id;

  @Column private String arid;

  @Column private String estabArid;

  @Column private String uprn;

  @Column private String addressType;

  @Column private String estabType;

  @Column private String addressLevel;

  @Column private String abpCode;

  @Column private String organisationName;

  @Column private String addressLine1;

  @Column private String addressLine2;

  @Column private String addressLine3;

  @Column private String townName;

  @Column private String postcode;

  @Column private String latitude;

  @Column private String longitude;

  @Column private String oa11cd;

  @Column private String lsoa11cd;

  @Column private String msoa11cd;

  @Column private String lad18cd;

  @Column private String rgn10cd;

  @Column private String htcWillingness;

  @Column private String htcDigital;

  @Column private String treatmentCode;

  @Column private String collectionExerciseId;

  @Column private String actionPlanId;

  @Column private String uacCode;

  @Column @Enumerated private CaseStatus status;
}
