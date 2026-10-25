package br.com.vr.miniautorizador.api.controller;

import br.com.vr.miniautorizador.api.dto.TransacaoRequest;
import br.com.vr.miniautorizador.application.AutorizarTransacaoIdempotente;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia.Execucao.Nova;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia.Execucao.Repetida;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao.Autorizada;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao.Recusada;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/transacoes")
class TransacaoController {

    static final String TRANSACAO_AUTORIZADA = "OK";
    static final String CABECALHO_CHAVE = "Idempotency-Key";
    static final String CABECALHO_REPETIDA = "Idempotent-Replayed";

    private final AutorizarTransacaoIdempotente autorizarTransacao;

    TransacaoController(AutorizarTransacaoIdempotente autorizarTransacao) {
        this.autorizarTransacao = autorizarTransacao;
    }

    /**
     * O switch sobre o resultado selado é exaustivo: um novo desfecho não compila
     * enquanto não tiver resposta HTTP definida aqui.
     *
     * <p>O corpo do contrato é o texto {@code OK} (ou o motivo da recusa), então a rota aceita
     * {@code text/plain}, além de {@code application/json}, que é o que muitos clientes mandam.</p>
     *
     * <p>O cabeçalho {@code Idempotency-Key} identifica a tentativa de compra: reenviar a mesma compra com a
     * mesma chave devolve a resposta original, sem debitar de novo, e a resposta repetida traz
     * {@code Idempotent-Replayed: true}. {@code CARTAO_BLOQUEADO} só aparece com o bloqueio por senhas
     * erradas ligado ({@code BLOQUEIO_SENHA_ATIVO=true}).</p>
     */
    @PostMapping(produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    ResponseEntity<String> autorizar(
            @RequestHeader(name = CABECALHO_CHAVE, required = false) Optional<String> chave,
            @Valid @RequestBody TransacaoRequest requisicao) {
        return switch (autorizarTransacao.executar(chave, requisicao.numeroCartao(), requisicao.senhaCartao(), requisicao.valor())) {
            case Nova(ResultadoAutorizacao resultado) -> responder(resultado, new HttpHeaders());
            case Repetida(ResultadoAutorizacao resultado) -> responder(resultado, cabecalhoDeRepeticao());
        };
    }

    private static ResponseEntity<String> responder(ResultadoAutorizacao resultado, HttpHeaders cabecalhos) {
        return switch (resultado) {
            case Autorizada() -> resposta(HttpStatus.CREATED, TRANSACAO_AUTORIZADA, cabecalhos);
            case Recusada(MotivoRecusa motivo) -> recusada(motivo, cabecalhos);
        };
    }

    private static HttpHeaders cabecalhoDeRepeticao() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.add(CABECALHO_REPETIDA, "true");
        return cabecalhos;
    }

    private static ResponseEntity<String> recusada(MotivoRecusa motivo, HttpHeaders cabecalhos) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, motivo.name(), cabecalhos);
    }

    private static ResponseEntity<String> resposta(HttpStatus status, String corpo, HttpHeaders cabecalhos) {
        return ResponseEntity.status(status)
                .headers(cabecalhos)
                .contentType(MediaType.TEXT_PLAIN)
                .body(corpo);
    }
}
