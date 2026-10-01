package com.jobpilot.profile.cv;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CvDatesTest {

    @ParameterizedTest
    @CsvSource({
            "2021-03, 2021-03-01",
            "2021-3, 2021-03-01",
            "2021-03-15, 2021-03-01",
            "03/2021, 2021-03-01",
            "2021, 2021-01-01",
            "Mar 2021, 2021-03-01",
            "March 2021, 2021-03-01",
            "Sept. 2019, 2019-09-01",
            "mars 2021, 2021-03-01",
            "Février 2020, 2020-02-01",
            "août 2018, 2018-08-01"})
    void parsesCommonFormats(String raw, LocalDate expected) {
        assertThat(CvDates.parse(raw)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Present", "Current", "Aujourd'hui", "now", "13/2021", "1800", "soon", "Q3 2021"})
    void returnsEmptyForOngoingOrUnknown(String raw) {
        assertThat(CvDates.parse(raw)).isEmpty();
    }
}
