package br.com.contadinho.cartoes.importacao;

/**
 * O arquivo da fatura não deu para ler. A mensagem vai para o usuário; o {@code codigo}, quando houver, diz ao
 * app o que fazer em seguida (pedir a senha do PDF, por exemplo).
 */
public final class ArquivoInvalido extends Exception {

	public enum Codigo {
		SENHA_PDF, SENHA_PDF_INCORRETA
	}

	private final Codigo codigo;

	public ArquivoInvalido(String mensagem) {
		this(mensagem, null);
	}

	public ArquivoInvalido(String mensagem, Codigo codigo) {
		super(mensagem);
		this.codigo = codigo;
	}

	public Codigo codigo() {
		return codigo;
	}

}
