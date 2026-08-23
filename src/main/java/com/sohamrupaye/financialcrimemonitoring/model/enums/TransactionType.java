package com.sohamrupaye.financialcrimemonitoring.model.enums;

/**
 * How the money moved. Persisted by name, so the order here is free to change.
 */
public enum TransactionType {

    TRANSFER,
    CASH_DEPOSIT,
    CASH_WITHDRAWAL,
    CARD_PAYMENT,
    INTERNATIONAL_TRANSFER
}
