package com.ncba.integration.service;

import com.ncba.integration.soap.client.*;
import jakarta.xml.bind.JAXBElement;
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

    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetIsoCode")
    @Retry(name = "soapService")
    public String getCountryIsoCode(String countryName) {
        CountryISOCode request = new CountryISOCode();
        request.setSCountryName(countryName);

        Object response = webServiceTemplate.marshalSendAndReceive(request);
        
        if (response instanceof JAXBElement) {
            response = ((JAXBElement<?>) response).getValue();
        }

        CountryISOCodeResponse isoResponse = (CountryISOCodeResponse) response;
        return isoResponse.getCountryISOCodeResult();
    }

    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetFullInfo")
    @Retry(name = "soapService")
    public TCountryInfo getFullCountryInfo(String isoCode) {
        FullCountryInfo request = new FullCountryInfo();
        request.setSCountryISOCode(isoCode);

        Object response = webServiceTemplate.marshalSendAndReceive(request);

        if (response instanceof JAXBElement) {
            response = ((JAXBElement<?>) response).getValue();
        }

        FullCountryInfoResponse fullInfoResponse = (FullCountryInfoResponse) response;
        return fullInfoResponse.getFullCountryInfoResult();
    }

    public String fallbackGetIsoCode(String countryName, Throwable t) {
        log.error("SOAP ISO Code Call Failed: {}", t.getMessage());
        throw new RuntimeException("SOAP Service is unavailable for ISO Code resolution: " + t.getMessage());
    }

    public TCountryInfo fallbackGetFullInfo(String isoCode, Throwable t) {
        log.error("SOAP Full Info Call Failed: {}", t.getMessage());
        throw new RuntimeException("SOAP Service is unavailable for Full Info resolution: " + t.getMessage());
    }
}