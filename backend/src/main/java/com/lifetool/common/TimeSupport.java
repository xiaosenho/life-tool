package com.lifetool.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TimeSupport {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    public static final Clock BUSINESS_CLOCK = Clock.system(BUSINESS_ZONE);

    private TimeSupport() {
    }

    public static LocalDate today() {
        return LocalDate.now(BUSINESS_CLOCK);
    }

    public static YearMonth currentMonth() {
        return YearMonth.now(BUSINESS_CLOCK);
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    public static Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    public static Instant startOfMonth(YearMonth month) {
        return month.atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    public static Instant startOfNextMonth(YearMonth month) {
        return month.plusMonths(1).atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    public static LocalDate toBusinessDate(Instant instant) {
        return LocalDateTime.ofInstant(instant, BUSINESS_ZONE).toLocalDate();
    }

    public static YearMonth toBusinessMonth(Instant instant) {
        return YearMonth.from(LocalDateTime.ofInstant(instant, BUSINESS_ZONE));
    }

    public static String businessDateSql() {
        return "(now() AT TIME ZONE 'Asia/Shanghai')::date";
    }

    public static TimestampRange dayRange(LocalDate date) {
        return new TimestampRange(startOfDay(date), startOfNextDay(date));
    }

    public static TimestampRange monthRange(YearMonth month) {
        return new TimestampRange(startOfMonth(month), startOfNextMonth(month));
    }

    public record TimestampRange(Instant startInclusive, Instant endExclusive) {
    }
}

