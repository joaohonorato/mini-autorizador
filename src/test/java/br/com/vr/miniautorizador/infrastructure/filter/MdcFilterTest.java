package br.com.vr.miniautorizador.infrastructure.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcFilterTest {

    private final MdcFilter filtro = new MdcFilter();

    @AfterEach
    void limpar() {
        MDC.clear();
    }

    @Test
    @DisplayName("sem X-Request-ID, gera um identificador, devolve no cabeçalho e o põe no contexto de log")
    void geraIdentificadorDeRastreio() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> durante = new AtomicReference<>();

        filtro.doFilter(new MockHttpServletRequest("POST", "/transacoes"), resposta, capturandoContexto(durante));

        String traceId = resposta.getHeader("X-Request-ID");
        assertThat(traceId).isNotBlank();
        assertThat(durante.get()).containsEntry("traceId", traceId)
                .containsEntry("httpMethod", "POST")
                .containsEntry("requestUri", "/transacoes");
    }

    @Test
    @DisplayName("com X-Request-ID, reaproveita o identificador")
    void reaproveitaIdentificadorRecebido() throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/cartoes/x");
        requisicao.addHeader("X-Request-ID", "rastreio-1");
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao, resposta, (req, res) -> { });

        assertThat(resposta.getHeader("X-Request-ID")).isEqualTo("rastreio-1");
    }

    @Test
    @DisplayName("o contexto de log é limpo ao fim da requisição, mesmo quando ela falha")
    void limpaOContextoAoFim() {
        FilterChain falha = (req, res) -> {
            throw new IllegalStateException("falha");
        };

        try {
            filtro.doFilter(new MockHttpServletRequest("GET", "/cartoes/x"), new MockHttpServletResponse(), falha);
        } catch (Exception esperada) {
            assertThat(esperada).isInstanceOf(IllegalStateException.class);
        }

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @ParameterizedTest(name = "o caminho {0} entra no contexto de log como {1}")
    @CsvSource({
            "/cartoes/6549873025634501, /cartoes/************4501",
            "/cartoes, /cartoes",
            "/transacoes, /transacoes",
            "/cartoes/123, /cartoes/123"
    })
    void mascaraONumeroDoCartaoNoCaminho(String caminho, String esperado) throws Exception {
        AtomicReference<Map<String, String>> durante = new AtomicReference<>();

        filtro.doFilter(new MockHttpServletRequest("GET", caminho), new MockHttpServletResponse(), capturandoContexto(durante));

        assertThat(durante.get()).containsEntry("requestUri", esperado);
    }

    private static FilterChain capturandoContexto(AtomicReference<Map<String, String>> destino) {
        return (req, res) -> destino.set(MDC.getCopyOfContextMap());
    }
}
