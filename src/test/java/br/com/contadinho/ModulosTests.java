package br.com.contadinho;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Garante as fronteiras do monólito modular: um módulo só usa a API pública do outro.
 */
class ModulosTests {

	private final ApplicationModules modulos = ApplicationModules.of(ContadinhoApiApplication.class);

	@Test
	void fronteirasDosModulosSaoRespeitadas() {
		modulos.verify();
	}

	@Test
	void documentaOsModulos() {
		new Documenter(modulos).writeDocumentation();
	}

}
