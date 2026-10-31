package br.com.contadinho.cartoes.domain;

/**
 * Bancos com modelo de importação na V1 (HU-06) e "Outro", que usa o mapeamento de CSV (HU-37).
 * O nome do enum é o que vai para o banco de dados e para a API; não renomeie.
 */
public enum Banco {

	NUBANK("Nubank"),
	ITAU("Itaú"),
	BRADESCO("Bradesco"),
	BANCO_DO_BRASIL("Banco do Brasil"),
	CAIXA("Caixa"),
	SANTANDER("Santander"),
	INTER("Inter"),
	C6("C6 Bank"),
	BTG("BTG Pactual"),
	MERCADO_PAGO("Mercado Pago"),
	OUTRO("Outro");

	/** RN-13: o modelo só fica verificado depois de testado com 2 faturas reais. Até lá, todos são beta. */
	public enum Modelo {
		VERIFICADO, BETA, SEM_MODELO
	}

	private final String nome;

	Banco(String nome) {
		this.nome = nome;
	}

	public String nome() {
		return nome;
	}

	public Modelo modelo() {
		return this == OUTRO ? Modelo.SEM_MODELO : Modelo.BETA;
	}

}
