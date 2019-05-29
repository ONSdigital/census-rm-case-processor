package uk.gov.ons.census.casesvc.model.entity;

import java.time.LocalDateTime;
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
  private LocalDateTime eventDate;

  @Column private String eventDescription;

  @Column private LocalDateTime rmEventProcessed;

  @Column
  @Enumerated(EnumType.STRING)
  private EventType eventType;
}
