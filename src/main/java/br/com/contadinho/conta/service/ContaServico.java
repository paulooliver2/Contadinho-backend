package br.com.contadinho.conta.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.conta.domain.ChavePix;
import br.com.contadinho.conta.domain.Usuario;
import br.com.contadinho.conta.repository.UsuarioRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Os dados do dono: a chave Pix das cobranças (HU-03) e o modelo da mensagem (HU-21). */
@Service
@Transactional(readOnly = true)
public class ContaServico {

	/** HU-03.1: a mensagem diz o formato esperado de cada tipo. */
	public static final Map<ChavePix.Tipo, String> CHAVE_INVALIDA = Map.of(
			ChavePix.Tipo.CPF, "Confira o CPF: são 11 números, como 123.456.789-09.",
			ChavePix.Tipo.EMAIL, "Confira o e-mail, como voce@email.com.",
			ChavePix.Tipo.CELULAR, "Informe DDD e celular, como (61) 99999-8888.",
			ChavePix.Tipo.ALEATORIA, "Cole a chave aleatória inteira, com os hífens.");

	/** HU-21.1: as únicas variáveis do modelo, exatamente assim. */
	public static final List<String> VARIAVEIS_COBRANCA = List.of("nome", "valor", "vencimento", "pix", "detalhe");

	static final String VARIAVEL_SEM_PAR = "Feche cada variável com { e }, como {nome}.";

	private final UsuarioRepositorio usuarios;

	public ContaServico(UsuarioRepositorio usuarios) {
		this.usuarios = usuarios;
	}

	public Usuario conta(UUID usuarioId) {
		return usuarios.findById(usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.UNAUTHORIZED, AutenticacaoServico.SESSAO_ENCERRADA));
	}

	@Transactional
	public Usuario definirPix(UUID usuarioId, ChavePix.Tipo tipo, String chave) {
		var normalizada = ChavePix.normalizar(tipo, chave)
			.orElseThrow(() -> ErroApi.campos(Map.of("chave", CHAVE_INVALIDA.get(tipo))));
		var usuario = conta(usuarioId);
		usuario.definirPix(tipo, normalizada);
		return usuario;
	}

	@Transactional
	public void removerPix(UUID usuarioId) {
		conta(usuarioId).removerPix();
	}

	/** HU-21. Texto em branco volta ao padrão; variável desconhecida ou chave sem par é 400 (HU-21.2). */
	@Transactional
	public Usuario definirModeloCobranca(UUID usuarioId, String modelo) {
		var texto = modelo == null || modelo.isBlank() ? null : modelo;
		if (texto != null) {
			validar(texto);
		}
		var usuario = conta(usuarioId);
		usuario.definirModeloCobranca(texto);
		return usuario;
	}

	private static void validar(String modelo) {
		int abre = -1;
		for (int i = 0; i < modelo.length(); i++) {
			char c = modelo.charAt(i);
			if (c == '{') {
				if (abre >= 0) {
					throw ErroApi.campos(Map.of("modelo", VARIAVEL_SEM_PAR));
				}
				abre = i;
			}
			else if (c == '}') {
				if (abre < 0) {
					throw ErroApi.campos(Map.of("modelo", VARIAVEL_SEM_PAR));
				}
				var nome = modelo.substring(abre + 1, i);
				if (!VARIAVEIS_COBRANCA.contains(nome)) {
					throw ErroApi.campos(Map.of("modelo",
							"Não conheço {" + nome + "}. Use {nome}, {valor}, {vencimento}, {pix} ou {detalhe}."));
				}
				abre = -1;
			}
		}
		if (abre >= 0) {
			throw ErroApi.campos(Map.of("modelo", VARIAVEL_SEM_PAR));
		}
	}

}
