package com.campus.lab_resource_platform.maintenance.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "maintenance")
@Getter
@Setter
@NoArgsConstructor
public class Maintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
