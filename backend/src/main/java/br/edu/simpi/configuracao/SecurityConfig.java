package br.edu.simpi.configuracao;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.edu.simpi.autenticacao.CustomAuthenticationEntryPoint;
import br.edu.simpi.autenticacao.JwtAuthenticationFilter;
import br.edu.simpi.autenticacao.TratamentoAcessoNegado;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final TratamentoAcessoNegado tratamentoAcessoNegado;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            TratamentoAcessoNegado tratamentoAcessoNegado) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.tratamentoAcessoNegado = tratamentoAcessoNegado;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            UrlBasedCorsConfigurationSource configuracaoCors) throws Exception {

        http
                .cors(cors -> cors.configurationSource(configuracaoCors))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated()

                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(tratamentoAcessoNegado))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())

                .httpBasic(basic -> basic.disable())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    UrlBasedCorsConfigurationSource configuracaoCors(
            @Value("${cors.origens-permitidas:}") String origensPermitidas) {
        var configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(Arrays.stream(origensPermitidas.split(","))
                .map(String::strip).filter(origem -> !origem.isEmpty()).distinct().toList());
        configuracao.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuracao.setExposedHeaders(List.of("Location"));
        configuracao.setAllowCredentials(false);
        var fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", configuracao);
        return fonte;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
