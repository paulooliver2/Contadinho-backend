package br.com.contadinho.conta.service;

import java.util.Map;
import java.util.UUID;

import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.conta.domain.ChavePix;
import br.com.contadinho.conta.domain.Usuario;
import br.com.contadinho.conta.repository.UsuarioRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Os dados do dono: por enquanto, a chave Pix das cobranças (HU-03). */
@Service
@Transactional(readOnly = true)
public class ContaServico {

	/** HU-03.1: a mensagem diz o formato esperado de cada tipo. */
	public static final Map<ChavePix.Tipo, String> CHAVE_INVALIDA = Map.of(
			ChavePix.Tipo.CPF, "Confira o CPF: são 11 números, como 123.456.789-09.",
			ChavePix.Tipo.EMAIL, "Confira o e-mail, como voce@email.com.",
			ChavePix.Tipo.CELULAR, "Informe DDD e celular, como (61) 99999-8888.",
			ChavePix.Tipo.ALEATORIA, "Cole a chave aleatória inteira, com os hífens.");

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

}
