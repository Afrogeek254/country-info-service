package com.ncba.integration.service;

import com.ncba.integration.entity.CountryInfo;
import com.ncba.integration.entity.Language;
import com.ncba.integration.exception.ResourceNotFoundException;
import com.ncba.integration.repository.CountryRepository;
import com.ncba.integration.soap.client.TCountryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
// fix the for otion 2 to check if the country already exists in the database before saving to avoid duplication error
import java.util.Optional;
 import java.util.stream.Collectors;

@Service
public class CountryService {

    private static final Logger log = LoggerFactory.getLogger(CountryService.class);
    private final SoapCountryService soapCountryService;
    private final CountryRepository countryRepository;

    public CountryService(SoapCountryService soapCountryService, CountryRepository countryRepository) {
        this.soapCountryService = soapCountryService;
        this.countryRepository = countryRepository;
    }

@Transactional
public CountryInfo processAndSaveCountry(String rawName) {
    String formattedName = toSentenceCase(rawName);
    log.info("[PROCESS] Searching for country: {}", formattedName);

    // 1. Fetch ISO Code from SOAP API
    String isoCode = soapCountryService.getCountryIsoCode(formattedName);
    if (isoCode == null || isoCode.contains("No country found")) {
        throw new ResourceNotFoundException("No valid country ISO code found for name: " + formattedName);
    }

    // 2. Check if already saved in MySQL to prevent duplicate key errors
    Optional<CountryInfo> existingCountry = countryRepository.findByIsoCode(isoCode);

    // 3. Fetch Full Info from SOAP API
    TCountryInfo fullInfo = soapCountryService.getFullCountryInfo(isoCode);

    // 4. Map Languages from SOAP response
    List<Language> languages = fullInfo.getLanguages().getTLanguage().stream()
            .map(l -> Language.builder()
                    .isoCode(l.getSISOCode())
                    .name(l.getSName())
                    .build())
            .collect(Collectors.toList());

    CountryInfo countryInfo;
    if (existingCountry.isPresent()) {
        // Update existing entity
        countryInfo = existingCountry.get();
        countryInfo.setName(fullInfo.getSName());
        countryInfo.setCapitalCity(fullInfo.getSCapitalCity());
        countryInfo.setPhoneCode(fullInfo.getSPhoneCode());
        countryInfo.setContinentCode(fullInfo.getSContinentCode());
        countryInfo.setCurrencyIsoCode(fullInfo.getSCurrencyISOCode());
        countryInfo.setCountryFlagUrl(fullInfo.getSCountryFlag());
        countryInfo.getLanguages().clear();
        countryInfo.getLanguages().addAll(languages);
        log.info("[DATABASE] Updating existing record for ISO: {}", isoCode);
    } else {
        // Create new entity
        countryInfo = CountryInfo.builder()
                .isoCode(fullInfo.getSISOCode())
                .name(fullInfo.getSName())
                .capitalCity(fullInfo.getSCapitalCity())
                .phoneCode(fullInfo.getSPhoneCode())
                .continentCode(fullInfo.getSContinentCode())
                .currencyIsoCode(fullInfo.getSCurrencyISOCode())
                .countryFlagUrl(fullInfo.getSCountryFlag())
                .languages(languages)
                .build();
        log.info("[DATABASE] Inserting new record for ISO: {}", isoCode);
    }

    return countryRepository.save(countryInfo);
}

    public List<CountryInfo> getAllCountries() {
        return countryRepository.findAll();
    }

    public CountryInfo getCountryById(Long id) {
        return countryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Country details not found for ID: " + id));
    }

    @Transactional
    public CountryInfo updateCountry(Long id, CountryInfo updatedDetails) {
        CountryInfo existing = getCountryById(id);
        existing.setName(updatedDetails.getName());
        existing.setCapitalCity(updatedDetails.getCapitalCity());
        existing.setPhoneCode(updatedDetails.getPhoneCode());
        existing.setContinentCode(updatedDetails.getContinentCode());
        existing.setCurrencyIsoCode(updatedDetails.getCurrencyIsoCode());
        existing.setCountryFlagUrl(updatedDetails.getCountryFlagUrl());
        return countryRepository.save(existing);
    }

    @Transactional
    public void deleteCountry(Long id) {
        CountryInfo existing = getCountryById(id);
        countryRepository.delete(existing);
        log.info("[DATABASE] Deleted country record with ID: {}", id);
    }

    /**
     * Utility method to convert string to Sentence Case (e.g. "kenya" -> "Kenya")
     */
    private String toSentenceCase(String input) {
        if (input == null || input.trim().isEmpty()) return input;
        String trimmed = input.trim();
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
    }
}