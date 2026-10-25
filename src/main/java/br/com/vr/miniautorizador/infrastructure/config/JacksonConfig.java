package br.com.vr.miniautorizador.infrastructure.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Campos de texto só aceitam texto JSON. Por padrão o Jackson converte um número ou booleano em
 * texto, e um {@code numeroCartao} enviado sem aspas perderia os zeros à esquerda sem aviso
 * (0000873025634501 viraria 873025634501), contradizendo a suposição de que o número é texto.
 * Esses valores passam a receber 400.
 *
 * <p>Chave repetida no JSON também recebe 400. Sem isso, repetir {@code valor} ou {@code senha}
 * quebra a desserialização do record e a API responde 500 para uma entrada do cliente.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer chaveRepetidaERejeitada() {
        return construtor -> construtor.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer camposDeTextoSoAceitamTexto() {
        return construtor -> construtor.postConfigurer(mapper -> {
            var texto = mapper.coercionConfigFor(LogicalType.Textual);
            texto.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            texto.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            texto.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
