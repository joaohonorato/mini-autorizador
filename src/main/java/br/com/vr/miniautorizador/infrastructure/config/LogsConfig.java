package br.com.vr.miniautorizador.infrastructure.config;

import br.com.vr.miniautorizador.infrastructure.filter.MdcFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra o filtro que coloca o contexto da requisição nos logs. Roda antes de
 * todos os outros, para que até a segurança já logue com o contexto.
 */
@Configuration
class LogsConfig {

    @Bean
    FilterRegistrationBean<MdcFilter> mdcFilter() {
        FilterRegistrationBean<MdcFilter> registro = new FilterRegistrationBean<>(new MdcFilter());
        registro.addUrlPatterns("/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
