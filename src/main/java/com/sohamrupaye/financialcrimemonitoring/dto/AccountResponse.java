package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;


public record AccountResponse(
        String accountNumber,
        String customerReference,
        AccountType accountType,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        LocalDate openedAt,
        Instant createdAt
) { }
