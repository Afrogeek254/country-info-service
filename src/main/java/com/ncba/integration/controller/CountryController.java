package com.ncba.integration.controller;

import com.ncba.integration.dto.CountryRequestDto;
import com.ncba.integration.entity.CountryInfo;
import com.ncba.integration.service.CountryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/countries")
public class CountryController {

    private final CountryService countryService;

    public CountryController(CountryService countryService) {
        this.countryService = countryService;
    }

    // Tasks 3, 4, 5: Post endpoint receiving name JSON -> SOAP integration -> Save DB
    @PostMapping("/fetch")
    public ResponseEntity<CountryInfo> fetchAndSave(@Valid @RequestBody CountryRequestDto request) {
        CountryInfo result = countryService.processAndSaveCountry(request.getName());
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    // Task 7: CRUD - Get All
    @GetMapping
    public ResponseEntity<List<CountryInfo>> getAll() {
        return ResponseEntity.ok(countryService.getAllCountries());
    }

    // Task 7: CRUD - Get By ID
    @GetMapping("/{id}")
    public ResponseEntity<CountryInfo> getById(@PathVariable Long id) {
        return ResponseEntity.ok(countryService.getCountryById(id));
    }

    // Task 7: CRUD - Update
    @PutMapping("/{id}")
    public ResponseEntity<CountryInfo> update(@PathVariable Long id, @RequestBody CountryInfo details) {
        return ResponseEntity.ok(countryService.updateCountry(id, details));
    }

    // Task 7: CRUD - Delete[cite: 4]
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        countryService.deleteCountry(id);
        return ResponseEntity.noContent().build();
    }
}