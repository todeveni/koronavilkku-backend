package fi.thl.covid19.exposurenotification.tokenverification;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.exposurenotification.error.TokenValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;

import static fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerificationServiceRest.PUBLISH_TOKEN_HEADER;
import static fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerificationServiceRest.TOKEN_VERIFICATION_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest
@ActiveProfiles({"test", "nodb"})
public class PublishTokenVerificationServiceRestTest {

    @Autowired
    private PublishTokenVerificationServiceRest verificationService;

    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate template;

    @MockBean
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    public void setUp() {
        server = MockRestServiceServer.createServer(template);
    }

    @AfterEach
    public void end() {
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    public void verificationWorks() throws Exception {
        PublishTokenVerification response = new PublishTokenVerification(123, LocalDate.now());
        server.expect(requestTo(TOKEN_VERIFICATION_PATH))
                .andExpect(header(PUBLISH_TOKEN_HEADER, "123456789012"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), APPLICATION_JSON));
        PublishTokenVerification verification = verificationService.getVerification("123456789012");
        assertEquals(response.id, verification.id);
        assertEquals(response.symptomsOnset, verification.symptomsOnset);
        server.verify();
    }

    @Test
    public void rejectionWorks() {
        server.expect(requestTo(TOKEN_VERIFICATION_PATH))
                .andExpect(header(PUBLISH_TOKEN_HEADER, "123456789012"))
                .andRespond(withNoContent());
        assertThrows(TokenValidationException.class, () -> verificationService.getVerification("123456789012"));
        server.verify();
    }

    @Test
    public void badRequestRejectsToken() {
        server.expect(requestTo(TOKEN_VERIFICATION_PATH))
                .andExpect(header(PUBLISH_TOKEN_HEADER, "123456789012"))
                .andRespond(withBadRequest());
        assertThrows(TokenValidationException.class, () -> verificationService.getVerification("123456789012"));
        server.verify();
    }

    @Test
    public void unauthorizedRequestRejectsToken() {
        server.expect(requestTo(TOKEN_VERIFICATION_PATH))
                .andExpect(header(PUBLISH_TOKEN_HEADER, "123456789012"))
                .andRespond(withUnauthorizedRequest());
        assertThrows(TokenValidationException.class, () -> verificationService.getVerification("123456789012"));
        server.verify();
    }

    @Test
    public void serverErrorLeadsToInternalError() {
        server.expect(requestTo(TOKEN_VERIFICATION_PATH))
                .andExpect(header(PUBLISH_TOKEN_HEADER, "123456789012"))
                .andRespond(withServerError());
        assertThrows(IllegalStateException.class, () -> verificationService.getVerification("123456789012"));
        server.verify();
    }
}
