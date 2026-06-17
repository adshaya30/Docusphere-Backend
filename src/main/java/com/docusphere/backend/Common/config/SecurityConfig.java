package com.docusphere.backend.Common.config;

import com.docusphere.backend.authentication.service.security.JwtAuthenticationFilter;
import com.docusphere.backend.authentication.service.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final PasswordEncoder passwordEncoder;
    private final AppConfig appConfig;

    public SecurityConfig(UserDetailsService userDetailsService, JwtAuthenticationFilter jwtAuthFilter,
            OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
            PasswordEncoder passwordEncoder,
            AppConfig appConfig) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.passwordEncoder = passwordEncoder;
        this.appConfig = appConfig;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .authorizationEndpoint(authz -> authz.baseUri("/oauth2/authorize"))
                        .redirectionEndpoint(redir -> redir.baseUri("/api/auth/oauth2/callback/*"))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/oauth2/**", "/api/auth/oauth2/callback/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/ocr/**").authenticated()
                        .requestMatchers("/api/share/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/*/download").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/onlyoffice/callback").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                            
                            String message = "Unauthorized access";
                            Exception jwtEx = (Exception) request.getAttribute("jwt_exception");
                            if (jwtEx != null) {
                                if (jwtEx instanceof io.jsonwebtoken.ExpiredJwtException) {
                                    message = "Token has expired";
                                } else if (jwtEx instanceof io.jsonwebtoken.security.SignatureException) {
                                    message = "Invalid token signature";
                                } else if (jwtEx instanceof io.jsonwebtoken.MalformedJwtException) {
                                    message = "Malformed token structure";
                                } else {
                                    message = "Invalid token: " + jwtEx.getMessage();
                                }
                            } else if (authException != null) {
                                message = authException.getMessage();
                            }
                            
                            response.getWriter().write(String.format("{\"error\": \"Unauthorized\", \"message\": \"%s\"}", message));
                        }))
                // Tell Spring Security to not create sessions since we're using JWTs
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(daoAuthenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        String frontendOrigin = normalizeOrigin(appConfig.getFrontendUrl());
        configuration.setAllowedOrigins(Arrays.asList(
                frontendOrigin
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
    
        provider.setPasswordEncoder(passwordEncoder);
    
        return provider;
    }

    //
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    private String normalizeOrigin(String origin) {
        if (origin == null) {
            return "http://localhost:5173";
        }

        String trimmed = origin.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }
}
