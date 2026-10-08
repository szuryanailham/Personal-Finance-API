package com.ilham.personal_finance_api.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ilham.personal_finance_api.repository.TransactionRepository;

@Component
public class TransactionCodeGenerator {

    private static final String TRANSACTION_CODE_PREFIX = "TRX-";
    private static final DateTimeFormatter TRANSACTION_CODE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private Clock clock;

    // format: TRX-yyyyMMdd-NNN, nomor urut reset setiap hari.
    // Membaca kode terakhir dari DB, jadi transaksi sebelumnya harus sudah di-flush.
    public String generate() {
        return generate(1).get(0);
    }

    // untuk bulk save: nomor urut dibaca sekali lalu dilanjutkan di memori,
    // karena transaksi dalam batch yang sama belum di-flush ke DB.
    public List<String> generate(int count) {
        String prefix = TRANSACTION_CODE_PREFIX + LocalDate.now(clock).format(TRANSACTION_CODE_DATE_FORMAT) + "-";

        int nextSequence = transactionRepository
            .findTopByTransactionCodeStartingWithOrderByTransactionCodeDesc(prefix)
            .map(last -> Integer.parseInt(last.getTransactionCode().substring(prefix.length())) + 1)
            .orElse(1);

        List<String> transactionCodes = new ArrayList<>(count);
        while (transactionCodes.size() < count) {
            String transactionCode = prefix + String.format("%03d", nextSequence++);
            if (!transactionRepository.existsByTransactionCode(transactionCode)) {
                transactionCodes.add(transactionCode);
            }
        }

        return transactionCodes;
    }
}
