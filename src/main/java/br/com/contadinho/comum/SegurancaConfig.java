package br.com.contadinho.comum;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API sem sessão: todo endpoint exige JWT, menos cadastro, login, refresh e recuperação de senha (RNF-01).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class SegurancaConfig {

	@Bean
	SecurityFilterChain api(HttpSecurity http) throws Exception {
		return http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/**", "/actuator/health/**", "/v3/api-docs/**").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
			.build();
	}

	/** O app Capacitor no Android chama a API com origem https://localhost. */
	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${contadinho.cors.origens}") List<String> origens) {
		var config = new CorsConfiguration();
		config.setAllowedOrigins(origens);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		var fonte = new UrlBasedCorsConfigurationSource();
		fonte.registerCorsConfiguration("/api/**", config);
		return fonte;
	}

	@Bean
	SecretKey chaveJwt(JwtProperties props) {
		return new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey chaveJwt) {
		return NimbusJwtDecoder.withSecretKey(chaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey chaveJwt) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(chaveJwt));
	}

	/** RNF-02: senhas com bcrypt. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
