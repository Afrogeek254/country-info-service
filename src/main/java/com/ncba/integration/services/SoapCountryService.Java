package com.ncba.integration.service;

import com.ncba.integration.soap.client.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

@Service
public class SoapCountryService {

    private static final Logger log = LoggerFactory.getLogger(SoapCountryService.class);
    private final WebServiceTemplate webServiceTemplate;

    public SoapCountryService(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    /**
     * Consume SOAP API to get ISO Code from Country Name
     */
    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetIsoCode")
    @Retry(name = "soapService")
    public String getCountryIsoCode(String countryName) {
        log.info("[SOAP OUTBOUND] Fetching ISO Code for country name: {}", countryName);
        CountryISOCode request = new CountryISOCode();
        request.setSCountryName(countryName);

        CountryISOCodeResponse response = (CountryISOCodeResponse) webServiceTemplate.marshalSendAndReceive(request);
        String result = response.getCountryISOCodeResult();
        log.info("[SOAP RESPONSE] Received ISO Code: {} for country: {}", result, countryName);
        return result;
    }

    /**
     * Consume SOAP API to get Full Country Info using ISO Code
     */
    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetFullInfo")
    @Retry(name = "soapService")
    public TCountryInfo getFullCountryInfo(String isoCode) {
        log.info("[SOAP OUTBOUND] Fetching Full Country Info for ISO: {}", isoCode);
        FullCountryInfo request = new FullCountryInfo();
        request.setSCountryISOCode(isoCode);

        FullCountryInfoResponse response = (FullCountryInfoResponse) webServiceTemplate.marshalSendAndReceive(request);
        log.info("[SOAP RESPONSE] Successfully retrieved full details for ISO: {}", isoCode);
        return response.getFullCountryInfoResult();
    }

    // Fallback Methods for Circuit Breaker
    public String fallbackGetIsoCode(String countryName, Throwable t) {
        log.error("[FALLBACK] Failure calling SOAP Service for ISO Code: {}", t.getMessage());
        throw new RuntimeException("External SOAP Service unavailable. Failed to resolve ISO code.");
    }

    public TCountryInfo fallbackGetFullInfo(String isoCode, Throwable t) {
        log.error("[FALLBACK] Failure calling SOAP Service for Full Info: {}", t.getMessage());
        throw new RuntimeException("External SOAP Service unavailable. Failed to fetch full country details.");
    }
}