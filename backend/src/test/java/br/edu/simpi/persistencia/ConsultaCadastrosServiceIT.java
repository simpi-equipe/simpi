package br.edu.simpi.persistencia;

import br.edu.simpi.consulta.dto.LocalizacaoSelecaoResponse;
import br.edu.simpi.consulta.dto.PesquisarBensRequest;
import br.edu.simpi.consulta.dto.SetorSelecaoResponse;
import br.edu.simpi.consulta.dto.UsuarioSelecaoResponse;
import br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.consulta.service.ExcecaoConsultaPatrimonial;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoCriacaoSolicitacao;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Transactional
class ConsultaCadastrosServiceIT {
    private final JdbcTemplate banco;
    private final ConsultaPatrimonialService consultas;
    private final UsuarioRepository usuarios;
    private final SolicitacaoService solicitacoes;
    private DadosSolicitacoesTeste dados;
    private Usuario ator;

    @Autowired
    ConsultaCadastrosServiceIT(JdbcTemplate banco,
            ConsultaPatrimonialService consultas, UsuarioRepository usuarios, SolicitacaoService solicitacoes) {
        this.banco = banco;
        this.consultas = consultas;
        this.usuarios = usuarios;
        this.solicitacoes = solicitacoes;
    }

    @BeforeEach
    void preparar() {
        dados = DadosSolicitacoesTeste.preparar(banco);
        ator = usuarios.findById(dados.solicitanteId()).orElseThrow();
    }

    @Test
    void setoresELocalizacoesRetornamSomenteAtivosOrdenadosPorNomeEId() {
        var terceiro = prepararOutroSetor();
        var inativo = prepararOutroSetor();
        List<DadosPatrimoniaisTeste> registros = List.of(dados.origem(), dados.destino(), terceiro);
        for (int indice = 0; indice < registros.size(); indice++) {
            String nome = indice == 0 ? "Bbbb sintético" : "Aaaa sintético";
            banco.update("UPDATE setor SET nome = ? WHERE id = ?", nome, registros.get(indice).setorId());
            banco.update("UPDATE localizacao SET nome = ? WHERE id = ?", nome, registros.get(indice).localizacaoId());
        }
        banco.update("UPDATE setor SET ativo = false WHERE id = ?", inativo.setorId());
        banco.update("UPDATE localizacao SET ativo = false WHERE id = ?", inativo.localizacaoId());
        Set<Long> setoresAlvo = Set.of(dados.origem().setorId(), dados.destino().setorId(), terceiro.setorId(), inativo.setorId());
        var setores = consultas.listarSetores(ator).stream().filter(item -> setoresAlvo.contains(item.id())).toList();
        assertEquals(List.of(dados.destino().setorId(), terceiro.setorId(), dados.origem().setorId()),
                setores.stream().map(SetorSelecaoResponse::id).toList());
        assertTrue(setores.stream().allMatch(item -> item.codigo() != null && item.nome() != null));
        Set<Long> locaisAlvo = Set.of(dados.origem().localizacaoId(), dados.destino().localizacaoId(), terceiro.localizacaoId(), inativo.localizacaoId());
        var locais = consultas.listarLocalizacoes(ator).stream().filter(item -> locaisAlvo.contains(item.id())).toList();
        assertEquals(List.of(dados.destino().localizacaoId(), terceiro.localizacaoId(), dados.origem().localizacaoId()),
                locais.stream().map(LocalizacaoSelecaoResponse::id).toList());
        assertEquals(Set.of("id", "codigo", "nome"), campos(SetorSelecaoResponse.class));
        assertEquals(Set.of("id", "codigo", "nome"), campos(LocalizacaoSelecaoResponse.class));
    }

    @Test
    void usuariosAtivosDeQualquerPerfilSaoSelecionadosMesmoEmSetorInativo() {
        Long setor = dados.destino().setorId();
        banco.update("UPDATE setor SET ativo = false WHERE id = ?", setor);
        banco.update("UPDATE usuario SET nome = 'Bbbb sintético' WHERE id = ?", dados.destino().gestorId());
        Long primeiro = inserirUsuario("Aaaa sintético", PerfilUsuario.COMUM, setor, true);
        Long segundo = inserirUsuario("Aaaa sintético", PerfilUsuario.ADMINISTRADOR, setor, true);
        inserirUsuario("Aaaa sintético", PerfilUsuario.GESTOR, setor, false);
        var resposta = consultas.listarUsuarios(setor, ator);
        assertEquals(List.of(primeiro, segundo, dados.destino().gestorId()), resposta.stream().map(UsuarioSelecaoResponse::id).toList());
        assertTrue(resposta.stream().allMatch(item -> item.setor().id().equals(setor)));
        assertTrue(resposta.stream().allMatch(item -> item.setor().nome() != null));
        assertEquals(Set.of("id", "nome", "setor"), campos(UsuarioSelecaoResponse.class));
        assertEquals(Set.of("id", "nome"), campos(UsuarioSelecaoResponse.SetorResponse.class));
    }

    @Test
    void setorExistenteSemUsuariosAtivosRetornaListaVazia() {
        banco.update("UPDATE usuario SET ativo = false WHERE setor_id = ?", dados.destino().setorId());
        assertEquals(List.of(), consultas.listarUsuarios(dados.destino().setorId(), ator));
    }

    @Test
    void setorObrigatorioInvalidoOuInexistenteUsaMotivosDoContrato() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            var falha = verificarFalha(DADOS_INVALIDOS, () -> consultas.listarUsuarios(id, ator));
            assertTrue(falha.getErros().containsKey("setorId"));
        }
        verificarFalha(RECURSO_NAO_ENCONTRADO, () -> consultas.listarUsuarios(Long.MAX_VALUE, ator));
    }

    @Test
    void consultasExigemAtorPersistidoAtivoSemConfiarNoObjetoRecebido() {
        verificarAtorRejeitado(null);
        Usuario inexistente = new Usuario("Ator sintético", "ator@example.invalid", DadosPatrimoniaisTeste.HASH_SINTETICO,
                PerfilUsuario.COMUM, ator.getSetor());
        verificarAtorRejeitado(inexistente);
        ReflectionTestUtils.setField(inexistente, "id", Long.MAX_VALUE);
        verificarAtorRejeitado(inexistente);
        banco.update("UPDATE usuario SET ativo = false WHERE id = ?", ator.getId());
        verificarAtorRejeitado(ator);
    }

    @Test
    void qualquerPerfilPodeConsultarCatalogosDeOutrosSetores() {
        for (PerfilUsuario perfil : PerfilUsuario.values()) {
            banco.update("UPDATE usuario SET perfil = ? WHERE id = ?", perfil.name(), ator.getId());
            assertFalse(consultas.listarSetores(ator).isEmpty());
            assertFalse(consultas.listarLocalizacoes(ator).isEmpty());
            assertEquals(dados.destino().gestorId(), consultas.listarUsuarios(dados.destino().setorId(), ator).getFirst().id());
        }
    }

    @Test
    void consultasNaoTornamDestinoInativoValidoParaCriacao() {
        var entrada = new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), "Justificativa sintética");
        banco.update("UPDATE setor SET ativo = false WHERE id = ?", dados.destino().setorId());
        assertFalse(consultas.listarUsuarios(dados.destino().setorId(), ator).isEmpty());
        var falha = assertThrows(ExcecaoCriacaoSolicitacao.class, () -> solicitacoes.criar(entrada, ator));
        assertEquals(MotivoFalhaCriacaoSolicitacao.DADOS_INVALIDOS, falha.getMotivo());
        assertEquals(0L, banco.queryForObject("SELECT count(*) FROM solicitacao WHERE bem_id = ?", Long.class, dados.origem().bemId()));
    }

    @Test
    void filtroPorLocalizacaoInativaNaoTornaEssaReferenciaValidaParaCriacao() {
        banco.update("UPDATE localizacao SET ativo = false WHERE id = ?", dados.destino().localizacaoId());
        var encontrados = consultas.pesquisarBens(new PesquisarBensRequest(null, dados.destino().setorId(),
                dados.destino().localizacaoId(), null, 0, 20), ator);
        assertEquals(1, encontrados.totalElementos());
        var entrada = new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), "Justificativa sintética");
        var falha = assertThrows(ExcecaoCriacaoSolicitacao.class, () -> solicitacoes.criar(entrada, ator));
        assertEquals(MotivoFalhaCriacaoSolicitacao.DADOS_INVALIDOS, falha.getMotivo());
        assertTrue(falha.getErros().containsKey("localizacaoDestinoId"));
    }

    private void verificarAtorRejeitado(Usuario usuario) {
        verificarFalha(NAO_AUTENTICADO, () -> consultas.listarSetores(usuario));
        verificarFalha(NAO_AUTENTICADO, () -> consultas.listarLocalizacoes(usuario));
        verificarFalha(NAO_AUTENTICADO, () -> consultas.listarUsuarios(dados.destino().setorId(), usuario));
    }

    private ExcecaoConsultaPatrimonial verificarFalha(MotivoFalhaConsultaPatrimonial motivo, Runnable operacao) {
        var falha = assertThrows(ExcecaoConsultaPatrimonial.class, operacao::run);
        assertEquals(motivo, falha.getMotivo());
        return falha;
    }

    private Long inserirUsuario(String nome, PerfilUsuario perfil, Long setorId, boolean ativo) {
        return banco.queryForObject("""
                INSERT INTO usuario (nome, email, senha_hash, perfil, setor_id, ativo) VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, nome, DadosPatrimoniaisTeste.novoEmail(), DadosPatrimoniaisTeste.HASH_SINTETICO,
                perfil.name(), setorId, ativo);
    }

    private DadosPatrimoniaisTeste prepararOutroSetor() {
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var outro = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        return outro;
    }

    private Set<String> campos(Class<?> tipo) {
        return Arrays.stream(tipo.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet());
    }
}
