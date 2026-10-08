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
        log.info("[PROCESS] Converted raw input '{}' to Sentence Case '{}'", rawName, formattedName);

        // Fetch ISO Code
        String isoCode = soapCountryService.getCountryIsoCode(formattedName);
        if ("No country found by that name".equalsIgnoreCase(isoCode)) {
            throw new ResourceNotFoundException("No valid country found for name: " + formattedName);
        }

        // Fetch Full Info from SOAP
        TCountryInfo fullInfo = soapCountryService.getFullCountryInfo(isoCode);

        // Map XML response to JPA Entity
        List<Language> languages = fullInfo.getLanguages().getTLanguage().stream()
                .map(l -> Language.builder().isoCode(l.getSISOCode()).name(l.getSName()).build())
                .collect(Collectors.toList());

        CountryInfo countryInfo = CountryInfo.builder()
                .isoCode(fullInfo.getSISOCode())
                .name(fullInfo.getSName())
                .capitalCity(fullInfo.getSCapitalCity())
                .phoneCode(fullInfo.getSPhoneCode())
                .continentCode(fullInfo.getSContinentCode())
                .currencyIsoCode(fullInfo.getSCurrencyISOCode())
                .countryFlagUrl(fullInfo.getSCountryFlag())
                .languages(languages)
                .build();

        CountryInfo saved = countryRepository.save(countryInfo);
        log.info("[DATABASE] Successfully persisted country details with ID: {}", saved.getId());
        return saved;
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