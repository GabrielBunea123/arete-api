package app.arete.arete_api.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class ProgramDayIntegrationTest extends AbstractProgramIntegrationTest {

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private final LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private final LocalDate lastMonday = thisMonday.minusWeeks(1);

    private Cookie owner;
    private long programId;

    @BeforeEach
    void createOwnerAndProgram() throws Exception {
        owner = registerAndGetAccessCookie();
        programId = createProgramAndGetId(owner);
    }

    @Test
    void eachDateHasItsOwnPlanAndUnplannedDatesAreRestDays() throws Exception {
        putDay(lastMonday, "Easy run");
        putDay(thisMonday, "Warm-up", "Intervals");

        final MockHttpServletResponse response = getCalendar(lastMonday, thisMonday.plusDays(6));

        assertThat(response.getStatus()).isEqualTo(200);
        final String calendar = response.getContentAsString();
        assertThat(readStrings(calendar, "$.days[*].date")).hasSize(14).startsWith(lastMonday.toString());
        assertThat(readStrings(calendar, "$.days[*].day.date"))
                .containsExactly(lastMonday.toString(), thisMonday.toString());
        assertThat(readStrings(calendar, "$.days[0].day.parts[*].title")).containsExactly("Easy run");
        assertThat(readStrings(calendar, "$.days[7].day.parts[*].title")).containsExactly("Warm-up", "Intervals");
        assertThat(JsonPath.<String>read(calendar, "$.days[7].dayOfWeek")).isEqualTo("MONDAY");
    }

    @Test
    void changingOneDateLeavesOtherDatesUnchanged() throws Exception {
        putDay(lastMonday, "Easy run");
        putDay(thisMonday, "Intervals");

        putDay(thisMonday, "Long run");

        assertThat(readStrings(getDay(lastMonday).getContentAsString(), "$.parts[*].title"))
                .containsExactly("Easy run");
        assertThat(readStrings(getDay(thisMonday).getContentAsString(), "$.parts[*].title"))
                .containsExactly("Long run");
    }

    @Test
    void feedbackRecordsStatusNotesAndPartResultsWithoutChangingThePlan() throws Exception {
        final LocalDate yesterday = today.minusDays(1);
        final String planned = putDay(yesterday, "Strength", "Conditioning").getContentAsString();
        final long strengthPartId = JsonPath.<Number>read(planned, "$.parts[0].id").longValue();

        final MockHttpServletResponse response = giveFeedback(yesterday, """
                {"status":"COMPLETED","notes":"Felt strong",
                 "parts":[{"partId":%d,"completed":true,"resultNotes":"Squat 5x5 at 100 kg"}]}
                """.formatted(strengthPartId));

        assertThat(response.getStatus()).isEqualTo(200);
        final String day = getDay(yesterday).getContentAsString();
        assertThat(JsonPath.<String>read(day, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<String>read(day, "$.notes")).isEqualTo("Felt strong");
        assertThat(readStrings(day, "$.parts[*].title")).containsExactly("Strength", "Conditioning");
        assertThat(JsonPath.<Boolean>read(day, "$.parts[0].completed")).isTrue();
        assertThat(JsonPath.<String>read(day, "$.parts[0].resultNotes")).isEqualTo("Squat 5x5 at 100 kg");
        assertThat(JsonPath.<Boolean>read(day, "$.parts[1].completed")).isFalse();
    }

    @Test
    void replanningADayClearsPartResultsButKeepsItsStatus() throws Exception {
        final LocalDate yesterday = today.minusDays(1);
        final long partId = JsonPath.<Number>read(putDay(yesterday, "Strength").getContentAsString(), "$.parts[0].id")
                .longValue();
        giveFeedback(yesterday, """
                {"status":"COMPLETED","parts":[{"partId":%d,"completed":true}]}
                """.formatted(partId));

        final String replanned = putDay(yesterday, "Mobility", "Intervals").getContentAsString();

        assertThat(JsonPath.<String>read(replanned, "$.status")).isEqualTo("COMPLETED");
        assertThat(readStrings(replanned, "$.parts[*].title")).containsExactly("Mobility", "Intervals");
        assertThat(JsonPath.<List<Boolean>>read(replanned, "$.parts[*].completed")).containsOnly(false);
    }

    @Test
    void futureDaysCanBeSkippedButNotCompleted() throws Exception {
        final LocalDate tomorrow = today.plusDays(1);
        putDay(tomorrow, "Easy run");

        assertThat(giveFeedback(tomorrow, """
                {"status":"COMPLETED"}
                """).getStatus()).isEqualTo(400);
        assertThat(giveFeedback(tomorrow, """
                {"status":"SKIPPED","notes":"Travelling"}
                """).getStatus()).isEqualTo(200);
        final String calendar = getCalendar(tomorrow, tomorrow).getContentAsString();
        assertThat(readStrings(calendar, "$.days[*].day.status")).containsExactly("SKIPPED");
    }

    @Test
    void feedbackOnAPartOfAnotherDayIsBadRequest() throws Exception {
        final LocalDate yesterday = today.minusDays(1);
        putDay(yesterday, "Strength");
        final long otherDayPartId = JsonPath.<Number>read(
                putDay(today, "Easy run").getContentAsString(), "$.parts[0].id").longValue();

        final MockHttpServletResponse response = giveFeedback(yesterday, """
                {"status":"COMPLETED","parts":[{"partId":%d,"completed":true}]}
                """.formatted(otherDayPartId));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }

    @Test
    void movingToARestDayMovesTheWholeDayAndStartsItAgainAsPlanned() throws Exception {
        final LocalDate thursday = thisMonday.plusDays(3);
        final LocalDate friday = thisMonday.plusDays(4);
        putDay(thursday, "Strength", "Conditioning");
        giveFeedback(thursday, """
                {"status":"SKIPPED","notes":"No time"}
                """);

        final MockHttpServletResponse response = moveDay(thursday, friday);

        assertThat(response.getStatus()).isEqualTo(200);
        final String moved = response.getContentAsString();
        assertThat(JsonPath.<String>read(moved, "$.date")).isEqualTo(friday.toString());
        assertThat(JsonPath.<String>read(moved, "$.status")).isEqualTo("PLANNED");
        assertThat(readStrings(moved, "$.parts[*].title")).containsExactly("Strength", "Conditioning");
        assertThat(getDay(thursday).getStatus()).isEqualTo(404);
    }

    @Test
    void movingOntoAPlannedDayAddsThePartsAfterItsOwn() throws Exception {
        final LocalDate thursday = thisMonday.plusDays(3);
        final LocalDate friday = thisMonday.plusDays(4);
        putDay(thursday, "Strength");
        putDay(friday, "Easy run");

        final String moved = moveDay(thursday, friday).getContentAsString();

        assertThat(readStrings(moved, "$.parts[*].title")).containsExactly("Easy run", "Strength");
        assertThat(getDay(thursday).getStatus()).isEqualTo(404);
    }

    @Test
    void completedDaysCantBeMovedAndADayCantMoveOntoItself() throws Exception {
        final LocalDate yesterday = today.minusDays(1);
        putDay(yesterday, "Strength");
        putDay(today, "Easy run");
        giveFeedback(yesterday, """
                {"status":"COMPLETED"}
                """);

        assertThat(moveDay(yesterday, today.plusDays(1)).getStatus()).isEqualTo(409);
        assertThat(moveDay(today, today).getStatus()).isEqualTo(400);
        assertThat(moveDay(today.plusDays(5), today.plusDays(6)).getStatus()).isEqualTo(404);
    }

    @Test
    void deletingADayMakesItARestDay() throws Exception {
        putDay(today, "Easy run");

        final MockHttpServletResponse response = deleteDay(today);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(getDay(today).getStatus()).isEqualTo(404);
        assertThat(deleteDay(today).getStatus()).isEqualTo(404);
        assertThat(readStrings(getCalendar(today, today).getContentAsString(), "$.days[*].day.id")).isEmpty();
    }

    @Test
    void dayListIsPaginatedMostRecentFirstAndFilterable() throws Exception {
        putDay(lastMonday, "A");
        putDay(lastMonday.plusDays(1), "B");
        putDay(lastMonday.plusDays(2), "C");
        giveFeedback(lastMonday, """
                {"status":"SKIPPED"}
                """);

        final String firstPage = listDays("size", "2").getContentAsString();
        final String skippedOnly = listDays("status", "SKIPPED").getContentAsString();
        final String inRange = listDays(
                "from", lastMonday.plusDays(1).toString(),
                "to", lastMonday.plusDays(2).toString(),
                "direction", "ASC").getContentAsString();

        assertThat(readStrings(firstPage, "$.content[*].date"))
                .containsExactly(lastMonday.plusDays(2).toString(), lastMonday.plusDays(1).toString());
        assertThat(JsonPath.<Number>read(firstPage, "$.totalElements").longValue()).isEqualTo(3);
        assertThat(readStrings(skippedOnly, "$.content[*].date")).containsExactly(lastMonday.toString());
        assertThat(readStrings(inRange, "$.content[*].parts[0].title")).containsExactly("B", "C");
    }

    @Test
    void invalidRequestsAreBadRequest() throws Exception {
        final List<MockHttpServletResponse> responses = List.of(
                putDayWithBody(today, """
                        {"parts":[]}
                        """),
                getCalendar(today, today.minusDays(1)),
                getCalendar(today, today.plusYears(1)),
                listDays("from", today.toString(), "to", today.minusDays(1).toString()),
                listDays("status", "BOGUS"));

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        });
    }

    @Test
    void anotherUsersProgramDaysAreNotFound() throws Exception {
        putDay(today, "Easy run");
        final Cookie ownerCookie = owner;
        owner = registerAndGetAccessCookie();

        final List<MockHttpServletResponse> responses = List.of(
                getCalendar(today, today),
                listDays(),
                getDay(today),
                putDay(today, "Hijacked"),
                giveFeedback(today, """
                        {"status":"SKIPPED"}
                        """),
                moveDay(today, today.plusDays(1)),
                deleteDay(today));

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus()).isEqualTo(404));
        owner = ownerCookie;
        assertThat(readStrings(getDay(today).getContentAsString(), "$.parts[*].title")).containsExactly("Easy run");
    }

    private MockHttpServletResponse putDay(final LocalDate date, final String... partTitles) throws Exception {
        final String parts = Arrays.stream(partTitles)
                .map("{\"title\":\"%s\"}"::formatted)
                .collect(Collectors.joining(","));
        return putDayWithBody(date, "{\"parts\":[" + parts + "]}");
    }

    private MockHttpServletResponse putDayWithBody(final LocalDate date, final String body) throws Exception {
        return perform(withCsrf(put("/programs/{programId}/days/{date}", programId, date))
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private MockHttpServletResponse giveFeedback(final LocalDate date, final String body) throws Exception {
        return perform(withCsrf(put("/programs/{programId}/days/{date}/feedback", programId, date))
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private MockHttpServletResponse moveDay(final LocalDate date, final LocalDate toDate) throws Exception {
        return perform(withCsrf(post("/programs/{programId}/days/{date}/move", programId, date))
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"toDate":"%s"}
                        """.formatted(toDate)));
    }

    private MockHttpServletResponse getDay(final LocalDate date) throws Exception {
        return perform(get("/programs/{programId}/days/{date}", programId, date).cookie(owner));
    }

    private MockHttpServletResponse deleteDay(final LocalDate date) throws Exception {
        return perform(withCsrf(delete("/programs/{programId}/days/{date}", programId, date)).cookie(owner));
    }

    private MockHttpServletResponse getCalendar(final LocalDate from, final LocalDate to) throws Exception {
        return perform(get("/programs/{programId}/calendar", programId)
                .cookie(owner)
                .param("from", from.toString())
                .param("to", to.toString()));
    }

    private MockHttpServletResponse listDays(final String... paramNamesAndValues) throws Exception {
        return perform(withParams(get("/programs/{programId}/days", programId).cookie(owner), paramNamesAndValues));
    }
}
