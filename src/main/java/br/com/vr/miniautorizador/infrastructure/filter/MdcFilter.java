package br.com.vr.miniautorizador.infrastructure.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Abre o contexto de log (MDC) de cada requisição: o identificador de rastreio, que volta no cabeçalho
 * {@code X-Request-ID}, o método, o caminho e o status. O caminho de {@code GET /cartoes/{numero}} traz o
 * número do cartão, então ele entra no log com os 12 primeiros dígitos mascarados.
 */
public class MdcFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(MdcFilter.class);
    static final String HEADER_REQUEST_ID = "X-Request-ID";
    private static final Pattern NUMERO_DE_CARTAO = Pattern.compile("\\d{12}(\\d{4})");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String traceId = Optional.ofNullable(httpRequest.getHeader(HEADER_REQUEST_ID))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        MDC.put(MdcChaves.TRACE_ID, traceId);
        MDC.put(MdcChaves.HTTP_METHOD, httpRequest.getMethod());
        MDC.put(MdcChaves.REQUEST_URI, mascararCartao(httpRequest.getRequestURI()));
        httpResponse.setHeader(HEADER_REQUEST_ID, traceId);

        long inicio = System.currentTimeMillis();
        try {
            LOG.debug("request.recebida");
            chain.doFilter(request, response);
        } finally {
            MDC.put(MdcChaves.HTTP_STATUS, String.valueOf(httpResponse.getStatus()));
            LOG.info("request.concluida duration_ms={}", System.currentTimeMillis() - inicio);
            MDC.clear();
        }
    }

    static String mascararCartao(String caminho) {
        return NUMERO_DE_CARTAO.matcher(caminho).replaceAll("************$1");
    }
}
