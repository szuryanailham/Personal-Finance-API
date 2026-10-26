package com.ilham.personal_finance_api.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.dto.CalendarDailyTypeTotal;
import com.ilham.personal_finance_api.dto.CalendarDayResponse;
import com.ilham.personal_finance_api.dto.CalendarTypeSummaryResponse;
import com.ilham.personal_finance_api.dto.TransactionCalendarResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.TransactionRepository;

@Service
public class TransactionCalendarService {

    // urutan tetap untuk totals & summaries (bukan urutan deklarasi enum)
    private static final List<TransactionType> CALENDAR_TYPE_ORDER =
        List.of(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.SAVING);

    private static final DateTimeFormatter MONTH_FORMAT =
        DateTimeFormatter.ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);

    private static final String MONTH_REQUIRED_MESSAGE = "month wajib diisi";
    private static final String MONTH_INVALID_MESSAGE = "month harus berformat YYYY-MM";

    @Autowired
    private TransactionRepository transactionRepository;

    // [MAIN] get calendar transaction
    @Transactional(readOnly = true)
    public TransactionCalendarResponse getCalendar(User user, String month) {
        YearMonth yearMonth = parseMonth(month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<CalendarDailyTypeTotal> rows = transactionRepository.sumDailyAmountAndCountByTypeAndPeriod(
            user,
            startDate.atStartOfDay(),
            endDate.plusDays(1).atStartOfDay()
        );

        return new TransactionCalendarResponse(
            yearMonth,
            startDate,
            endDate,
            toTotals(rows),
            toDays(rows)
        );
    }

    // [HELPER] format harus persis YYYY-MM, tidak ada fallback ke bulan berjalan
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MONTH_REQUIRED_MESSAGE);
        }
        try {
            return YearMonth.parse(month, MONTH_FORMAT);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MONTH_INVALID_MESSAGE);
        }
    }

    // [HELPER] total sebulan per type; type tanpa transaksi = 0
    private List<CalendarTypeSummaryResponse> toTotals(List<CalendarDailyTypeTotal> rows) {
        return CALENDAR_TYPE_ORDER.stream()
            .map(type -> sumRowsOfType(type, rows))
            .toList();
    }

    // [HELPER]
    private CalendarTypeSummaryResponse sumRowsOfType(TransactionType type, List<CalendarDailyTypeTotal> rows) {
        List<CalendarDailyTypeTotal> rowsOfType = rows.stream()
            .filter(row -> type.name().equals(row.type()))
            .toList();

        BigDecimal amount = rowsOfType.stream()
            .map(CalendarDailyTypeTotal::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = rowsOfType.stream()
            .mapToLong(CalendarDailyTypeTotal::count)
            .sum();

        return new CalendarTypeSummaryResponse(type, amount, count);
    }

    // [HELPER] hanya tanggal yang punya transaksi, urut naik
    private List<CalendarDayResponse> toDays(List<CalendarDailyTypeTotal> rows) {
        Map<LocalDate, List<CalendarDailyTypeTotal>> rowsByDate = rows.stream()
            .collect(Collectors.groupingBy(CalendarDailyTypeTotal::date, TreeMap::new, Collectors.toList()));

        return rowsByDate.entrySet().stream()
            .map(entry -> new CalendarDayResponse(entry.getKey(), toTotals(entry.getValue())))
            .toList();
    }
}
