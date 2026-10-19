package com.campus.lab_resource_platform.utilization.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "utilization")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}

