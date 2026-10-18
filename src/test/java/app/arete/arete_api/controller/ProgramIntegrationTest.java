package app.arete.arete_api.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class ProgramIntegrationTest extends AbstractProgramIntegrationTest {

    @Test
    void createdProgramsAreListedOnlyForTheirOwner() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        final Cookie otherUser = registerAndGetAccessCookie();

        final MockHttpServletResponse createResponse = createProgram(owner, "Elite Athlete");

        assertThat(createResponse.getStatus()).isEqualTo(201);
        assertThat(createResponse.getContentAsString()).contains("Elite Athlete").contains("createdAt");
        assertThat(readStrings(listPrograms(owner).getContentAsString(), "$.content[*].name"))
                .containsExactly("Elite Athlete");
        final String otherUsersPage = listPrograms(otherUser).getContentAsString();
        assertThat(readStrings(otherUsersPage, "$.content[*].name")).isEmpty();
        assertThat(JsonPath.<Number>read(otherUsersPage, "$.totalElements").longValue()).isZero();
    }

    @Test
    void programListIsPaginatedNewestFirstByDefault() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        createProgram(owner, "Alpha");
        createProgram(owner, "Bravo");
        createProgram(owner, "Charlie");

        final String firstPage = listPrograms(owner, "size", "2").getContentAsString();
        final String secondPage = listPrograms(owner, "size", "2", "page", "1").getContentAsString();

        assertThat(readStrings(firstPage, "$.content[*].name")).containsExactly("Charlie", "Bravo");
        assertThat(JsonPath.<Number>read(firstPage, "$.totalElements").longValue()).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(firstPage, "$.totalPages")).isEqualTo(2);
        assertThat(readStrings(secondPage, "$.content[*].name")).containsExactly("Alpha");
        assertThat(JsonPath.<Integer>read(secondPage, "$.page")).isEqualTo(1);
    }

    @Test
    void programListCanBeSortedByName() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        createProgram(owner, "Bravo");
        createProgram(owner, "Charlie");
        createProgram(owner, "Alpha");

        final String page = listPrograms(owner, "sort", "NAME", "direction", "ASC").getContentAsString();

        assertThat(readStrings(page, "$.content[*].name")).containsExactly("Alpha", "Bravo", "Charlie");
    }

    @Test
    void programListFiltersByNameIgnoringCaseAndTreatingWildcardsLiterally() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        createProgram(owner, "Elite Athlete");
        createProgram(owner, "Athlete");
        createProgram(owner, "100% Effort");

        final String eliteOnly = listPrograms(owner, "name", "ELITE").getContentAsString();
        final String percentOnly = listPrograms(owner, "name", "%").getContentAsString();

        assertThat(readStrings(eliteOnly, "$.content[*].name")).containsExactly("Elite Athlete");
        assertThat(readStrings(percentOnly, "$.content[*].name")).containsExactly("100% Effort");
    }

    @Test
    void programListFiltersByCreationDate() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        createProgram(owner, "Athlete");
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);

        final String createdToday = listPrograms(owner,
                "createdFrom", today.toString(), "createdTo", today.toString()).getContentAsString();
        final String createdBeforeToday = listPrograms(owner,
                "createdTo", today.minusDays(1).toString()).getContentAsString();

        assertThat(readStrings(createdToday, "$.content[*].name")).containsExactly("Athlete");
        assertThat(readStrings(createdBeforeToday, "$.content[*].name")).isEmpty();
    }

    @Test
    void invalidProgramListParametersAreBadRequest() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        final String today = LocalDate.now(ZoneOffset.UTC).toString();
        final String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();

        final List<MockHttpServletResponse> responses = List.of(
                listPrograms(owner, "size", "500"),
                listPrograms(owner, "page", "-1"),
                listPrograms(owner, "sort", "BOGUS"),
                listPrograms(owner, "createdFrom", today, "createdTo", yesterday));

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        });
    }

    @Test
    void anotherUsersProgramIsNotFound() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        final Cookie otherUser = registerAndGetAccessCookie();
        final long programId = createProgramAndGetId(owner);

        assertThat(perform(get("/programs/{programId}", programId).cookie(otherUser)).getStatus()).isEqualTo(404);
        assertThat(perform(withCsrf(delete("/programs/{programId}", programId)).cookie(otherUser)).getStatus())
                .isEqualTo(404);
        assertThat(perform(get("/programs/{programId}", programId).cookie(owner)).getStatus()).isEqualTo(200);
    }

    @Test
    void updatedProgramHasTheNewNameAndDescription() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        final long programId = createProgramAndGetId(owner);

        final MockHttpServletResponse response = perform(withCsrf(put("/programs/{programId}", programId))
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Committed","description":"Three days a week"}
                        """));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Committed").contains("Three days a week");
    }

    @Test
    void deletedProgramIsGoneTogetherWithItsDays() throws Exception {
        final Cookie owner = registerAndGetAccessCookie();
        final long programId = createProgramAndGetId(owner);
        final String date = LocalDate.now(ZoneOffset.UTC).toString();
        perform(withCsrf(put("/programs/{programId}/days/{date}", programId, date))
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"parts":[{"title":"Easy run"}]}
                        """));

        final MockHttpServletResponse response =
                perform(withCsrf(delete("/programs/{programId}", programId)).cookie(owner));

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(perform(get("/programs/{programId}", programId).cookie(owner)).getStatus()).isEqualTo(404);
        assertThat(perform(get("/programs/{programId}/days/{date}", programId, date).cookie(owner)).getStatus())
                .isEqualTo(404);
    }

    private MockHttpServletResponse listPrograms(
            final Cookie accessCookie,
            final String... paramNamesAndValues) throws Exception {
        return perform(withParams(get("/programs").cookie(accessCookie), paramNamesAndValues));
    }
}
