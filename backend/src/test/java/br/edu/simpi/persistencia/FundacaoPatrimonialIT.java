package br.edu.simpi.persistencia;

import br.edu.simpi.bem.BemPatrimonial;
import br.edu.simpi.bem.BemPatrimonialRepository;
import br.edu.simpi.localizacao.Localizacao;
import br.edu.simpi.localizacao.LocalizacaoRepository;
import br.edu.simpi.setor.Setor;
import br.edu.simpi.setor.SetorRepository;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Transactional
class FundacaoPatrimonialIT {

    private final JdbcTemplate banco;
    private final EntityManager gerenciador;
    private final SetorRepository setores;
    private final UsuarioRepository usuarios;
    private final LocalizacaoRepository localizacoes;
    private final BemPatrimonialRepository bens;
    private DadosPatrimoniaisTeste dados;

    @Autowired
    FundacaoPatrimonialIT(JdbcTemplate banco, EntityManager gerenciador,
            SetorRepository setores, UsuarioRepository usuarios,
            LocalizacaoRepository localizacoes, BemPatrimonialRepository bens) {
        this.banco = banco;
        this.gerenciador = gerenciador;
        this.setores = setores;
        this.usuarios = usuarios;
        this.localizacoes = localizacoes;
        this.bens = bens;
    }

    @BeforeEach
    void prepararDadosSinteticos() {
        dados = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS ALL IMMEDIATE");
    }

    @Test
    void releEntidadesRelacionamentosEValoresPadrao() {
        gerenciador.clear();
        BemPatrimonial bem = bens.findById(dados.bemId()).orElseThrow();
        Setor setor = setores.findById(dados.setorId()).orElseThrow();
        Usuario gestor = usuarios.findById(dados.gestorId()).orElseThrow();
        Localizacao localizacao = localizacoes.findById(dados.localizacaoId()).orElseThrow();

        assertEquals(setor.getId(), bem.getSetor().getId());
        assertEquals(localizacao.getId(), bem.getLocalizacao().getId());
        assertEquals(gestor.getId(), bem.getResponsavel().getId());
        assertEquals(gestor.getId(), setor.getGestor().getId());
        assertEquals(setor.getId(), gestor.getSetor().getId());
        assertEquals(PerfilUsuario.GESTOR, gestor.getPerfil());
        assertTrue(setor.isAtivo());
        assertTrue(gestor.isAtivo());
        assertTrue(localizacao.isAtivo());
        assertTrue(bem.isAtivo());
        assertNull(localizacao.getDescricao());
        assertNull(bem.getPlaqueta());
    }

    @Test
    void persisteEReleTodosOsPerfisComoTexto() {
        Setor setor = setores.getReferenceById(dados.setorId());
        for (PerfilUsuario perfil : PerfilUsuario.values()) {
            Usuario usuario = usuarios.saveAndFlush(new Usuario("Usuário sintético",
                    DadosPatrimoniaisTeste.novoEmail(), DadosPatrimoniaisTeste.HASH_SINTETICO, perfil, setor));
            Long usuarioId = usuario.getId();
            gerenciador.clear();
            assertEquals(perfil, usuarios.findById(usuarioId).orElseThrow().getPerfil());
            assertEquals(perfil.name(), banco.queryForObject(
                    "SELECT perfil FROM usuario WHERE id = ?", String.class, usuarioId));
            setor = setores.getReferenceById(dados.setorId());
        }
    }

    @Test
    void persisteLocalizacaoEBensComPlaquetasRepetidasOuAusentes() {
        Localizacao localizacao = localizacoes.saveAndFlush(new Localizacao(
                DadosPatrimoniaisTeste.novoCodigo(), "Localização sintética", null));
        Long localizacaoId = localizacao.getId();
        for (String plaqueta : new String[] {"PLAQUETA-SINTETICA", "PLAQUETA-SINTETICA", null}) {
            BemPatrimonial bem = bens.saveAndFlush(new BemPatrimonial(
                    DadosPatrimoniaisTeste.novoCodigo(), plaqueta, "Bem sintético",
                    setores.getReferenceById(dados.setorId()), localizacao,
                    usuarios.getReferenceById(dados.gestorId())));
            Long bemId = bem.getId();
            gerenciador.clear();
            BemPatrimonial recarregado = bens.findById(bemId).orElseThrow();
            assertEquals(plaqueta, recarregado.getPlaqueta());
            assertEquals(localizacaoId, recarregado.getLocalizacao().getId());
            assertTrue(recarregado.isAtivo());
            localizacao = localizacoes.getReferenceById(localizacaoId);
        }
        assertNull(localizacoes.findById(localizacaoId).orElseThrow().getDescricao());
    }

    @Test
    void designaSomenteGestorDoProprioSetor() {
        Setor setor = setores.findById(dados.setorId()).orElseThrow();
        Usuario comum = usuarios.saveAndFlush(new Usuario("Usuário sintético",
                DadosPatrimoniaisTeste.novoEmail(), DadosPatrimoniaisTeste.HASH_SINTETICO,
                PerfilUsuario.COMUM, setor));
        // A carga circular exige adiar a FK que o preparo inicial tornou imediata.
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        DadosPatrimoniaisTeste outroCenario = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        Usuario gestorDeOutroSetor = usuarios.findById(outroCenario.gestorId()).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> setor.designarGestor(comum));
        assertThrows(IllegalArgumentException.class, () -> setor.designarGestor(gestorDeOutroSetor));
        assertThrows(NullPointerException.class, () -> setor.designarGestor(null));
        assertEquals(dados.gestorId(), setor.getGestor().getId());

        Usuario novoGestor = usuarios.saveAndFlush(new Usuario("Gestor sintético",
                DadosPatrimoniaisTeste.novoEmail(), DadosPatrimoniaisTeste.HASH_SINTETICO,
                PerfilUsuario.GESTOR, setor));
        setor.designarGestor(novoGestor);
        setores.flush();
        gerenciador.clear();
        assertEquals(novoGestor.getId(), setores.findById(dados.setorId()).orElseThrow().getGestor().getId());
    }
}
