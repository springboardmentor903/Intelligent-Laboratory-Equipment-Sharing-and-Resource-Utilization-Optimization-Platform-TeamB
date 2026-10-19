package com.campus.lab_resource_platform.sharing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sharing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sharing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}

