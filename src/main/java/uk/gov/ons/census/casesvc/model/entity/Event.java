package uk.gov.ons.census.casesvc.model.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import lombok.Data;

@Data
@Entity
public class Event {
  @Id private UUID id;

  @ManyToOne private UacQidLink uacQidLink;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime eventDate;

  @Column private String eventDescription;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime rmEventProcessed;

  @Column
  @Enumerated(EnumType.STRING)
  private EventType eventType;
}
