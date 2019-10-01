package uk.gov.ons.census.casesvc.model.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "cases")
public class Case {

  @Id private int caseRef;

  @Column private UUID caseId;

  @Column private String arid;

  @Column private String estabArid;

  @Column private String uprn;

  @Column private String caseType;

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

  @Column private String oa;

  @Column private String lsoa;

  @Column private String msoa;

  @Column private String lad;

  @Column private String region;

  @Column private String htcWillingness;

  @Column private String htcDigital;

  @Column private String fieldCoordinatorId;

  @Column private String fieldOfficerId;

  @Column private String treatmentCode;

  @Column private String ceExpectedCapacity;

  @Column private String collectionExerciseId;

  @Column private String actionPlanId;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime createdDateTime;

  @Column
  @Enumerated(EnumType.STRING)
  private CaseState state;

  @OneToMany(mappedBy = "caze")
  List<UacQidLink> uacQidLinks;

  @OneToMany(mappedBy = "caze")
  List<Event> events;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean receiptReceived;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean refusalReceived;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean addressInvalid;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean undeliveredAsAddressed;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean ccsCase;
}
