package com.ncba.integration.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "country_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String isoCode;

    @Column(nullable = false)
    private String name;

    private String capitalCity;
    private String phoneCode;
    private String continentCode;
    private String currencyIsoCode;
    private String countryFlagUrl;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "country_id")
    @Builder.Default
    private List<Language> languages = new ArrayList<>();
}