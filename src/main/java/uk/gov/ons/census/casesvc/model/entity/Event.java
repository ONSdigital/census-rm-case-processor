package uk.gov.ons.census.casesvc.model.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
public class Event {
  @Id private UUID id;

  @ManyToOne private UacQidLink uacQidLink;

  @Column private LocalDateTime eventDate;

  @Column private String eventDescription;

  @Column private LocalDateTime rmEventProcessed;
}
