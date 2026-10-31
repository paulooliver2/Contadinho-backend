package br.com.contadinho.conta.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.conta.domain.Usuario;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepositorio extends JpaRepository<Usuario, UUID> {

	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

}
