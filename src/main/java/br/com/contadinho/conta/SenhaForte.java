package br.com.contadinho.conta;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RN-02: pelo menos 10 caracteres, com letras e números. Vale no cadastro e na senha nova (HU-40).
 * O teto de 64 respeita o limite de 72 bytes do bcrypt.
 */
@NotNull(message = "Crie uma senha.")
@Size(min = 10, max = 64, message = "A senha precisa ter de 10 a 64 caracteres.")
@Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "A senha precisa ter letras e números.")
@Constraint(validatedBy = {})
@Target({ FIELD, METHOD, PARAMETER })
@Retention(RUNTIME)
@Documented
@interface SenhaForte {

	String message() default "";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
