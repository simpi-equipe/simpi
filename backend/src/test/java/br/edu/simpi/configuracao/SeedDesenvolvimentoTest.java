package br.edu.simpi.configuracao;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeedDesenvolvimentoTest {
    private JdbcTemplate banco;
    private PasswordEncoder senhas;
    private DatabaseMetaData metadados;

    @BeforeEach
    void preparar() throws Exception {
        banco = mock(JdbcTemplate.class);
        senhas = mock(PasswordEncoder.class);
        Connection conexao = mock(Connection.class);
        metadados = mock(DatabaseMetaData.class);
        when(conexao.getMetaData()).thenReturn(metadados);
        when(metadados.getURL()).thenReturn("jdbc:postgresql://127.0.0.1:5434/simpi_dev");
        when(banco.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<String>>any()))
                .thenAnswer(chamada -> chamada.<ConnectionCallback<String>>getArgument(0).doInConnection(conexao));
        when(banco.queryForObject("SELECT current_database()", String.class)).thenReturn("simpi_dev");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://servidor/simpi_dev", "jdbc:postgresql://postgres:5432/simpi_dev",
            "jdbc:postgresql://localhost/simpi_dev?currentSchema=outro"})
    void bloquearDestinoRemotoOuAmbiguoAntesDeQualquerCarga(String endereco) throws Exception {
        when(metadados.getURL()).thenReturn(endereco);
        assertThrows(IllegalStateException.class, () -> new SeedDesenvolvimento(banco, senhas, "senha-teste").run());
        verificarSemEscrita();
    }

    @ParameterizedTest
    @ValueSource(strings = {"simpi_test", "postgres", "template0", "template1"})
    void bloquearBancoDeIntegracaoESistema(String nome) {
        when(banco.queryForObject("SELECT current_database()", String.class)).thenReturn(nome);
        assertThrows(IllegalStateException.class, () -> new SeedDesenvolvimento(banco, senhas, "senha-teste").run());
        verificarSemEscrita();
    }

    @Test
    void exigirSenhaExplicita() {
        assertThrows(IllegalStateException.class, () -> new SeedDesenvolvimento(banco, senhas, " ").run());
        verificarSemEscrita();
    }

    @Test
    void reexecucaoNaoRedefineDadosOuSenhas() {
        when(banco.queryForObject(anyString(), eq(Integer.class))).thenReturn(19);
        new SeedDesenvolvimento(banco, senhas, "senha-nova").run();
        verificarSemEscrita();
    }

    @Test
    void cargaParcialNaoSobrescreveRegistros() {
        when(banco.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> new SeedDesenvolvimento(banco, senhas, "senha-teste").run());
        verificarSemEscrita();
    }

    private void verificarSemEscrita() {
        verify(banco, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(senhas);
        verify(banco, never()).execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
    }
}
