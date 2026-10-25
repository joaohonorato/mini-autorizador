package br.com.vr.miniautorizador.infrastructure.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Optional;

/**
 * Basic Auth em todos os endpoints. O usuário vem de spring.security.user.* (application.yml).
 * CSRF desabilitado porque a API é stateless e não usa cookies de sessão.
 *
 * <p>O servidor encaminha internamente as respostas de erro (um 401, um 500) para {@code /error};
 * esse encaminhamento é liberado pelo tipo de despacho, não pela URL. Assim, uma requisição feita
 * diretamente a {@code /error} não chega à página de erro: recebe 401 sem credenciais e 403 com.</p>
 */
@Configuration
public class SegurancaConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").denyAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    /**
     * O usuário em memória padrão do Spring ignora maiúsculas no login. Aqui o login precisa ser
     * exatamente o configurado, como a senha. Cada busca devolve uma cópia, porque o Spring apaga
     * a senha do objeto depois de autenticar.
     */
    @Bean
    UserDetailsService usuarioDaApi(SecurityProperties propriedades) {
        SecurityProperties.User configurado = propriedades.getUser();
        UserDetails usuario = User.withUsername(configurado.getName())
                .password("{noop}" + configurado.getPassword())
                .roles(configurado.getRoles().toArray(String[]::new))
                .build();
        return login -> Optional.of(usuario)
                .filter(u -> u.getUsername().equals(login))
                .map(u -> User.withUserDetails(u).build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
