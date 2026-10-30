package br.com.contadinho.comum;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Id do usuário dono do token ({@code sub} do JWT). Use num parâmetro {@code UUID} do controller e filtre toda
 * consulta por ele (RNF-01): {@code List<Cartao> listar(@UsuarioLogado UUID usuarioId)}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.util.UUID).fromString(subject)")
@Parameter(hidden = true)
public @interface UsuarioLogado {

}
