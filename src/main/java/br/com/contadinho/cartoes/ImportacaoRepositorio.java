package br.com.contadinho.cartoes;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ImportacaoRepositorio extends JpaRepository<Importacao, UUID> {

}
