package io.github.maoz3000.miniwsa.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeFilterTest {

    private static final int LIMIT = 10;

    private final RequestSizeFilter filter = new RequestSizeFilter(LIMIT, JsonMapper.builder().build());

    @Test
    void rejectsDeclaredOversizedBodyWithoutCallingChain() throws Exception {
        MockHttpServletRequest request = request("{\"too\":\"large\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains(RequestSizeFilter.tooLargeMessage(LIMIT));
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void passesBodyWithinLimit() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("{\"a\":1}"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest().getInputStream().readAllBytes())
                .asString(StandardCharsets.UTF_8).isEqualTo("{\"a\":1}");
    }

    @Test
    void cutsOffBodyWithUnderstatedLength() throws Exception {
        MockHttpServletRequest request = request("{\"too\":\"large\"}");
        request.removeHeader("Content-Length");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new UnknownLengthRequest(request), new MockHttpServletResponse(), chain);

        assertThatThrownBy(() -> chain.getRequest().getInputStream().readAllBytes())
                .isInstanceOf(PayloadTooLargeException.class);
    }

    private static MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/events/ingest");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static final class UnknownLengthRequest extends jakarta.servlet.http.HttpServletRequestWrapper {

        UnknownLengthRequest(MockHttpServletRequest request) {
            super(request);
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }
}
