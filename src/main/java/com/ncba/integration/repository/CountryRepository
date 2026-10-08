package com.ncba.integration.repository;

import com.ncba.integration.entity.CountryInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CountryRepository extends JpaRepository<CountryInfo, Long> {
    Optional<CountryInfo> findByIsoCode(String isoCode);
}