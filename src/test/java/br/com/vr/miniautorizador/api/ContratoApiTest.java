package br.com.vr.miniautorizador.api;

import br.com.vr.miniautorizador.application.AutorizarTransacaoIdempotente;
import br.com.vr.miniautorizador.application.ConsultarSaldo;
import br.com.vr.miniautorizador.application.CriarCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaInvalidaException;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaReutilizadaException;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia.Execucao;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.infrastructure.config.JacksonConfig;
import br.com.vr.miniautorizador.infrastructure.config.SegurancaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP do enunciado: status, corpo e autenticação.
 * Os casos de uso são substituídos por mocks porque o foco é a tradução para HTTP;
 * o comportamento deles é coberto pelos testes de cada caso de uso e pelos cenários Cucumber.
 */
@WebMvcTest
@Import({SegurancaConfig.class, JacksonConfig.class})
class ContratoApiTest {

    private static final String NUMERO = "6549873025634501";
    private static final String NOVO_CARTAO = """
            {"numeroCartao": "%s", "senha": "1234"}""".formatted(NUMERO);
    private static final String TRANSACAO = """
            {"numeroCartao": "%s", "senhaCartao": "1234", "valor": 10.00}""".formatted(NUMERO);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CriarCartao criarCartao;
    @MockBean
    private ConsultarSaldo consultarSaldo;
    @MockBean
    private AutorizarTransacaoIdempotente autorizarTransacao;

    @ParameterizedTest(name = "POST /cartoes com resultado {0} retorna {1} e ecoa o corpo")
    @CsvSource({
            "CRIADO,       201",
            "JA_EXISTENTE, 422"
    })
    void criacaoEcoaCorpo(ResultadoCriacao resultado, int statusEsperado) throws Exception {
        when(criarCartao.executar(NUMERO, "1234")).thenReturn(resultado);

        mvc.perform(post("/cartoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content(NOVO_CARTAO))
                .andExpect(status().is(statusEsperado))
                .andExpect(content().string("{\"senha\":\"1234\",\"numeroCartao\":\"" + NUMERO + "\"}"));

        verify(criarCartao).executar(NUMERO, "1234");
    }

    @Test
    @DisplayName("POST /cartoes com número inválido retorna 400 com o detalhe")
    void criacaoComNumeroInvalido() throws Exception {
        when(criarCartao.executar(any(), any()))
                .thenThrow(new ValorInvalidoException("Número do cartão deve ter 16 dígitos"));

        mvc.perform(post("/cartoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content("{\"numeroCartao\": \"123\", \"senha\": \"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Número do cartão deve ter 16 dígitos"));
    }

    @ParameterizedTest(name = "POST /cartoes com {0} retorna 400 apontando o campo {1}, sem chamar o caso de uso")
    @CsvSource(delimiter = '|', textBlock = """
            {"numeroCartao": 6549873025634501, "senha": "1234"}   | numeroCartao
            {"numeroCartao": "6549873025634501", "senha": 1234}   | senha
            {"numeroCartao": "6549873025634501", "senha": 12.5}   | senha
            {"numeroCartao": "6549873025634501", "senha": true}   | senha
            """)
    void criacaoComCampoDeTextoQueNaoETexto(String corpo, String campo) throws Exception {
        mvc.perform(post("/cartoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Campo " + campo + " com tipo ou valor inválido"));

        verifyNoInteractions(criarCartao);
    }

    @ParameterizedTest(name = "POST /transacoes com {0} retorna 400 apontando o campo {1}, sem chamar o caso de uso")
    @CsvSource(delimiter = '|', textBlock = """
            {"numeroCartao": 6549873025634501, "senhaCartao": "1234", "valor": 10.00}    | numeroCartao
            {"numeroCartao": "6549873025634501", "senhaCartao": 1234, "valor": 10.00}    | senhaCartao
            {"numeroCartao": "6549873025634501", "senhaCartao": false, "valor": 10.00}   | senhaCartao
            {"numeroCartao": "6549873025634501", "senhaCartao": "1234", "valor": "abc"}  | valor
            """)
    void transacaoComCampoDeTextoQueNaoETexto(String corpo, String campo) throws Exception {
        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Campo " + campo + " com tipo ou valor inválido"));

        verifyNoInteractions(autorizarTransacao);
    }

    @ParameterizedTest(name = "POST {0} com corpo malformado ou vazio retorna 400 sem chamar casos de uso")
    @CsvSource(delimiter = '|', textBlock = """
            /cartoes    | {"numeroCartao": "6549873025634501", "senha":
            /cartoes    | não é JSON
            /cartoes    | []
            /transacoes | {"numeroCartao": "6549873025634501", "senhaCartao": "1234", "valor": 10.00
            /transacoes | ''
            /transacoes | []
            """)
    void corpoMalformadoOuVazio(String caminho, String corpo) throws Exception {
        mvc.perform(post(caminho).with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corpo da requisição ausente ou malformado"));

        verifyNoInteractions(criarCartao, autorizarTransacao);
    }

    @Test
    @DisplayName("POST /cartoes sem campos obrigatórios retorna 400 sem chamar o caso de uso")
    void criacaoSemCamposObrigatorios() throws Exception {
        mvc.perform(post("/cartoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Campos inválidos: numeroCartao não pode ficar em branco; senha não pode ficar em branco"));

        verifyNoInteractions(criarCartao);
    }

    @ParameterizedTest(name = "com Accept \"{0}\", as rotas de cartão respondem JSON")
    @ValueSource(strings = {"application/json", "*/*", "application/json, text/plain, */*"})
    void acceptCompativelNasRotasDeCartao(String accept) throws Exception {
        when(criarCartao.executar(NUMERO, "1234")).thenReturn(ResultadoCriacao.CRIADO);
        when(consultarSaldo.executar(NUMERO)).thenReturn(Optional.of(Dinheiro.de("495.15")));

        mvc.perform(post("/cartoes").with(httpBasic("username", "password"))
                        .header("Accept", accept).contentType(APPLICATION_JSON).content(NOVO_CARTAO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCartao").value(NUMERO));
        mvc.perform(get("/cartoes/" + NUMERO).with(httpBasic("username", "password")).header("Accept", accept))
                .andExpect(status().isOk())
                .andExpect(content().string("495.15"));
    }

    @ParameterizedTest(name = "com Accept \"{0}\", as rotas de cartão recusam com 406 sem chamar o caso de uso")
    @ValueSource(strings = {"text/plain", "application/xml"})
    void acceptIncompativelNasRotasDeCartao(String accept) throws Exception {
        mvc.perform(post("/cartoes").with(httpBasic("username", "password"))
                        .header("Accept", accept).contentType(APPLICATION_JSON).content(NOVO_CARTAO))
                .andExpect(status().isNotAcceptable());
        mvc.perform(get("/cartoes/" + NUMERO).with(httpBasic("username", "password")).header("Accept", accept))
                .andExpect(status().isNotAcceptable());

        verifyNoInteractions(criarCartao, consultarSaldo);
    }

    @ParameterizedTest(name = "com Accept \"{0}\", POST /transacoes responde o texto OK")
    @ValueSource(strings = {"application/json", "text/plain", "*/*", "text/plain, application/json, application/*+json, */*"})
    void acceptCompativelNaTransacao(String accept) throws Exception {
        when(autorizarTransacao.executar(Optional.empty(), NUMERO, "1234", new BigDecimal("10.00")))
                .thenReturn(nova(ResultadoAutorizacao.autorizada()));

        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .header("Accept", accept).contentType(APPLICATION_JSON).content(TRANSACAO))
                .andExpect(status().isCreated())
                .andExpect(content().string("OK"));
    }

    @Test
    @DisplayName("POST /transacoes com Accept incompatível retorna 406 sem chamar o caso de uso")
    void acceptIncompativelNaTransacao() throws Exception {
        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .header("Accept", "application/xml").contentType(APPLICATION_JSON).content(TRANSACAO))
                .andExpect(status().isNotAcceptable());

        verifyNoInteractions(autorizarTransacao);
    }

    @Test
    @DisplayName("GET /cartoes/{numero} retorna o saldo como número puro")
    void consultaRetornaSaldo() throws Exception {
        when(consultarSaldo.executar(NUMERO)).thenReturn(Optional.of(Dinheiro.de("495.15")));

        mvc.perform(get("/cartoes/" + NUMERO).with(httpBasic("username", "password")))
                .andExpect(status().isOk())
                .andExpect(content().string("495.15"));
    }

    @Test
    @DisplayName("GET /cartoes/{numero} de cartão inexistente retorna 404 sem corpo")
    void consultaDeCartaoInexistente() throws Exception {
        when(consultarSaldo.executar(NUMERO)).thenReturn(Optional.empty());

        mvc.perform(get("/cartoes/" + NUMERO).with(httpBasic("username", "password")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("POST /transacoes autorizada retorna 201 com OK")
    void transacaoAutorizada() throws Exception {
        when(autorizarTransacao.executar(Optional.empty(), NUMERO, "1234", new BigDecimal("10.00")))
                .thenReturn(nova(ResultadoAutorizacao.autorizada()));

        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content(TRANSACAO))
                .andExpect(status().isCreated())
                .andExpect(content().string("OK"));

        verify(autorizarTransacao).executar(Optional.empty(), NUMERO, "1234", new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("POST /transacoes com Idempotency-Key repassa a chave ao caso de uso e não marca a resposta como repetida")
    void transacaoComChaveDeIdempotencia() throws Exception {
        when(autorizarTransacao.executar(Optional.of("compra-1"), NUMERO, "1234", new BigDecimal("10.00")))
                .thenReturn(nova(ResultadoAutorizacao.autorizada()));

        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .header("Idempotency-Key", "compra-1").contentType(APPLICATION_JSON).content(TRANSACAO))
                .andExpect(status().isCreated())
                .andExpect(content().string("OK"))
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @ParameterizedTest(name = "POST /transacoes repetida com resultado {0} devolve o mesmo status e corpo, marcada com Idempotent-Replayed")
    @CsvSource({
            "AUTORIZADA,        201, OK",
            "SALDO_INSUFICIENTE, 422, SALDO_INSUFICIENTE",
            "SENHA_INVALIDA,    422, SENHA_INVALIDA"
    })
    void transacaoRepetida(String resultado, int statusEsperado, String corpo) throws Exception {
        ResultadoAutorizacao original = resultado.equals("AUTORIZADA")
                ? ResultadoAutorizacao.autorizada()
                : ResultadoAutorizacao.recusada(MotivoRecusa.valueOf(resultado));
        when(autorizarTransacao.executar(Optional.of("compra-1"), NUMERO, "1234", new BigDecimal("10.00")))
                .thenReturn(new Execucao.Repetida(original));

        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .header("Idempotency-Key", "compra-1").contentType(APPLICATION_JSON).content(TRANSACAO))
                .andExpect(status().is(statusEsperado))
                .andExpect(content().string(corpo))
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    @DisplayName("POST /transacoes com Idempotency-Key já usada em outra requisição retorna 422 em ProblemDetail")
    void chaveDeIdempotenciaReutilizada() throws Exception {
        when(autorizarTransacao.executar(any(), any(), any(), any())).thenThrow(new ChaveIdempotenciaReutilizadaException());

        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .header("Idempotency-Key", "compra-1").contentType(APPLICATION_JSON).content(TRANSACAO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Idempotency-Key já foi usada em outra requisição"));
    }

    @Test
    @DisplayName("POST /transacoes com Idempotency-Key fora do formato retorna 400 com o detalhe")
    void chaveDeIdempotenciaInvalida() throws Exception {
        when(autorizarTransacao.executar(any(), any(), any(), any()))
                .thenThrow(new ChaveIdempotenciaInvalidaException("Idempotency-Key deve ter de 1 a 64 caracteres"));

        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .header("Idempotency-Key", "chave inválida").contentType(APPLICATION_JSON).content(TRANSACAO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Idempotency-Key deve ter de 1 a 64 caracteres"));
    }

    private static Execucao nova(ResultadoAutorizacao resultado) {
        return new Execucao.Nova(resultado);
    }

    @ParameterizedTest(name = "POST /transacoes recusada por {0} retorna 422 com o motivo no corpo")
    @EnumSource(MotivoRecusa.class)
    void transacaoRecusada(MotivoRecusa motivo) throws Exception {
        when(autorizarTransacao.executar(any(), any(), any(), any())).thenReturn(nova(ResultadoAutorizacao.recusada(motivo)));

        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content(TRANSACAO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(motivo.name()));
    }

    @ParameterizedTest(name = "POST /transacoes com número \"{0}\" e senha \"{1}\" chega às regras e retorna 422 {2}")
    @CsvSource({
            "'',               1234, CARTAO_INEXISTENTE",
            "6549873025634501, '',   SENHA_INVALIDA",
            "6549873025634501, '  ', SENHA_INVALIDA"
    })
    void transacaoComCampoEmBrancoChegaAsRegras(String numero, String senha, MotivoRecusa motivo) throws Exception {
        when(autorizarTransacao.executar(Optional.empty(), numero, senha, new BigDecimal("10.00")))
                .thenReturn(nova(ResultadoAutorizacao.recusada(motivo)));

        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"numeroCartao": "%s", "senhaCartao": "%s", "valor": 10.00}""".formatted(numero, senha)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(motivo.name()));
    }

    @Test
    @DisplayName("POST /transacoes sem campos obrigatórios retorna 400 sem chamar o caso de uso")
    void transacaoSemCamposObrigatorios() throws Exception {
        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Campos inválidos: numeroCartao é obrigatório; senhaCartao é obrigatório; valor é obrigatório"));

        verifyNoInteractions(autorizarTransacao);
    }

    @ParameterizedTest(name = "POST /transacoes com valor {0} retorna 400 \"{1}\" sem chamar o caso de uso")
    @CsvSource(delimiter = '|', textBlock = """
            -1   | Campos inválidos: valor deve ser maior que zero
            0    | Campos inválidos: valor deve ser maior que zero
            null | Campos inválidos: valor é obrigatório
            """)
    void transacaoComValorInvalido(String valor, String detalhe) throws Exception {
        mvc.perform(post("/transacoes").with(httpBasic("username", "password"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"numeroCartao": "%s", "senhaCartao": "1234", "valor": %s}""".formatted(NUMERO, valor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detalhe));

        verifyNoInteractions(autorizarTransacao);
    }

    @ParameterizedTest(name = "{0} sem credenciais válidas retorna 401 sem chamar casos de uso")
    @MethodSource("requisicoesSemCredenciaisValidas")
    void semCredenciaisValidas(String descricao, MockHttpServletRequestBuilder requisicao) throws Exception {
        mvc.perform(requisicao).andExpect(status().isUnauthorized());

        verifyNoInteractions(criarCartao, consultarSaldo, autorizarTransacao);
    }

    static Stream<Arguments> requisicoesSemCredenciaisValidas() {
        return Stream.of(
                Arguments.of("GET /cartoes/{numero}", get("/cartoes/" + NUMERO)),
                Arguments.of("POST /cartoes", post("/cartoes").contentType(APPLICATION_JSON).content(NOVO_CARTAO)),
                Arguments.of("POST /transacoes", post("/transacoes").contentType(APPLICATION_JSON).content(TRANSACAO)),
                Arguments.of("GET /cartoes/{numero} com senha errada",
                        get("/cartoes/" + NUMERO).with(httpBasic("username", "errada"))),
                Arguments.of("POST /cartoes com senha errada",
                        post("/cartoes").with(httpBasic("username", "errada")).contentType(APPLICATION_JSON).content(NOVO_CARTAO)),
                Arguments.of("POST /transacoes com senha errada",
                        post("/transacoes").with(httpBasic("username", "errada")).contentType(APPLICATION_JSON).content(TRANSACAO)),
                Arguments.of("GET /cartoes/{numero} com usuário desconhecido e a senha certa",
                        get("/cartoes/" + NUMERO).with(httpBasic("outro", "password"))),
                Arguments.of("POST /transacoes com usuário desconhecido e a senha certa",
                        post("/transacoes").with(httpBasic("outro", "password")).contentType(APPLICATION_JSON).content(TRANSACAO)),
                Arguments.of("GET /cartoes/{numero} com login em maiúsculas",
                        get("/cartoes/" + NUMERO).with(httpBasic("USERNAME", "password"))),
                Arguments.of("GET /error", get("/error")));
    }

    @Test
    @DisplayName("GET /error acessado diretamente retorna 403 mesmo autenticado")
    void erroAcessadoDiretamente() throws Exception {
        mvc.perform(get("/error").with(httpBasic("username", "password")))
                .andExpect(status().isForbidden());
    }
}
