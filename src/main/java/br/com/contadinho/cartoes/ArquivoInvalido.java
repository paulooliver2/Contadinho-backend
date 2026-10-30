package br.com.contadinho.cartoes;

/**
 * O arquivo da fatura não deu para ler. A mensagem vai para o usuário; o {@code codigo}, quando houver, diz ao
 * app o que fazer em seguida (pedir a senha do PDF, por exemplo).
 */
final class ArquivoInvalido extends Exception {

	enum Codigo {
		SENHA_PDF, SENHA_PDF_INCORRETA
	}

	private final Codigo codigo;

	ArquivoInvalido(String mensagem) {
		this(mensagem, null);
	}

	ArquivoInvalido(String mensagem, Codigo codigo) {
		super(mensagem);
		this.codigo = codigo;
	}

	Codigo codigo() {
		return codigo;
	}

}
