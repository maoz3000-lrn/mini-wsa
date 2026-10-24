package io.github.maoz3000.miniwsa.ingest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Rejects oversized ingest bodies before they are parsed. A declared Content-Length above the limit is
 * answered with 413 immediately; bodies without (or with an understated) Content-Length are cut off while
 * being read, surfacing as {@link PayloadTooLargeException}.
 */
@Slf4j
public class RequestSizeFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeFilter(long maxBodyBytes, ObjectMapper objectMapper) {
        this.maxBodyBytes = maxBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            log.warn("Rejected ingest request with Content-Length {} (limit {})",
                    request.getContentLengthLong(), maxBodyBytes);
            writeTooLarge(response);
            return;
        }
        chain.doFilter(new BoundedRequest(request, maxBodyBytes), response);
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, tooLargeMessage(maxBodyBytes));
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    static String tooLargeMessage(long maxBodyBytes) {
        return "request body exceeds the limit of " + maxBodyBytes + " bytes; split it into smaller requests";
    }

    private static final class BoundedRequest extends HttpServletRequestWrapper {

        private final long limit;

        BoundedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new BoundedInputStream(super.getInputStream(), limit);
        }
    }

    private static final class BoundedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        BoundedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            count(b == -1 ? 0 : 1);
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            count(Math.max(n, 0));
            return n;
        }

        private void count(int n) {
            read += n;
            if (read > limit) {
                throw new PayloadTooLargeException(tooLargeMessage(limit));
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
