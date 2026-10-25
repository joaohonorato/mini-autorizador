package br.com.vr.miniautorizador.api.controller;

import br.com.vr.miniautorizador.api.dto.NovoCartaoRequest;
import br.com.vr.miniautorizador.application.ConsultarSaldo;
import br.com.vr.miniautorizador.application.CriarCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@RestController
@RequestMapping("/cartoes")
class CartaoController {

    private static final Map<ResultadoCriacao, HttpStatus> STATUS_POR_RESULTADO = new EnumMap<>(Map.of(
            ResultadoCriacao.CRIADO, HttpStatus.CREATED,
            ResultadoCriacao.JA_EXISTENTE, HttpStatus.UNPROCESSABLE_ENTITY));

    private final CriarCartao criarCartao;
    private final ConsultarSaldo consultarSaldo;

    CartaoController(CriarCartao criarCartao, ConsultarSaldo consultarSaldo) {
        this.criarCartao = criarCartao;
        this.consultarSaldo = consultarSaldo;
    }

    /**
     * O contrato devolve o próprio corpo da requisição tanto na criação (201)
     * quanto quando o cartão já existe (422).
     *
     * <p>As rotas de cartão respondem só JSON. {@code produces} faz a negociação de conteúdo acontecer
     * antes do caso de uso: um cliente que não aceita JSON recebe 406 sem que o cartão seja criado.</p>
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<NovoCartaoRequest> criar(@Valid @RequestBody NovoCartaoRequest requisicao) {
        ResultadoCriacao resultado = criarCartao.executar(requisicao.numeroCartao(), requisicao.senha());
        return ResponseEntity.status(STATUS_POR_RESULTADO.get(resultado)).body(requisicao);
    }

    @GetMapping(value = "/{numeroCartao}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BigDecimal> consultarSaldo(
            @PathVariable String numeroCartao) {
        return consultarSaldo.executar(numeroCartao)
                .map(Dinheiro::valor)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
