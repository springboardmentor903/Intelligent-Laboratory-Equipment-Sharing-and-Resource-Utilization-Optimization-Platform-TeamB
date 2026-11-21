package com.campus.lab_resource_platform.maintenance.entity;

public enum ScheduleRepeatPeriod {
    DAILY(1),
    WEEKLY(7),
    BIWEEKLY(14),
    MONTHLY(30),
    QUARTERLY(90),
    SEMI_ANNUALLY(180),
    ANNUALLY(365);

    private final int days;

    ScheduleRepeatPeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
