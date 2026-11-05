package br.edu.simpi.persistencia;

import br.edu.simpi.consulta.dto.PaginaTransferenciasBemResponse;
import br.edu.simpi.consulta.dto.TransferenciaBemResponse;
import br.edu.simpi.consulta.dto.TransferenciaBemResponse.ReferenciasTransferenciaResponse;
import br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.consulta.service.ExcecaoConsultaPatrimonial;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.RegrasVisibilidadeSolicitacao;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
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
class ConsultaTransferenciasBemServiceIT {
    @Autowired private JdbcTemplate banco;
    @Autowired private EntityManager gerenciador;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SolicitacaoRepository solicitacoes;
    @Autowired private ConsultaPatrimonialService consultas;
    @Autowired private SolicitacaoService operacoes;
    private DadosSolicitacoesTeste dados;
    private Usuario ator;

    @BeforeEach
    void preparar() {
        dados = DadosSolicitacoesTeste.preparar(banco);
        ator = usuario(dados.solicitanteId());
    }

    @Test
    void incluiSomenteConcluidasDoBemInformado() {
        Long concluida = inserirConcluida("2027-01-01T10:00:00Z");
        Long outroBem = inserirConcluida("2027-01-01T11:00:00Z");
        banco.update("UPDATE solicitacao SET bem_id = ? WHERE id = ?", dados.destino().bemId(), outroBem);
        for (var estado : StatusSolicitacao.values()) {
            if (estado == StatusSolicitacao.CONCLUIDA) {
                continue;
            }
            Long excluida = dados.inserirSolicitacao(banco, estado.name());
            assertEquals(List.of(concluida), ids(historico(ator)));
            assertEquals(1, historico(ator).totalElementos());
            // Libera o índice de um pedido em andamento por bem para o próximo cenário.
            banco.update("UPDATE solicitacao SET status = 'CANCELADA' WHERE id = ?", excluida);
        }
        assertEquals(List.of(outroBem), ids(consultas.listarTransferencias(dados.destino().bemId(), null, null, ator)));
    }

    @Test
    void visibilidadeConsideraTresSetoresHistoricosEAdministradorGlobal() {
        Long pedido = inserirConcluida("2027-01-01T10:00:00Z");
        var terceiro = prepararOutroSetor();
        var externo = prepararOutroSetor();
        banco.update("UPDATE solicitacao SET setor_solicitante_id = ? WHERE id = ?", terceiro.setorId(), pedido);
        var solicitacao = solicitacoes.findById(pedido).orElseThrow();
        for (Long usuarioId : List.of(terceiro.gestorId(), dados.origem().gestorId(), dados.destino().gestorId())) {
            Usuario participante = usuario(usuarioId);
            assertTrue(RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, participante));
            assertEquals(List.of(pedido), ids(historico(participante)));
        }
        Usuario administrador = usuario(externo.gestorId());
        assertFalse(RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, administrador));
        assertEquals(0, historico(administrador).totalElementos());
        // O setor atual do bem não concede acesso a um pedido histórico.
        banco.update("UPDATE bem_patrimonial SET setor_id = ? WHERE id = ?", externo.setorId(), dados.origem().bemId());
        assertTrue(historico(administrador).conteudo().isEmpty());
        banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR' WHERE id = ?", administrador.getId());
        assertEquals(List.of(pedido), ids(historico(administrador)));
        assertTrue(RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, administrador));
    }

    @Test
    void ordenaPorRecebimentoDecrescenteEIdComoDesempateAntesDePaginar() {
        Long antigo = inserirConcluida("2027-01-01T09:00:00Z");
        Long primeiroEmpatado = inserirConcluida("2027-01-02T10:00:00Z");
        Long segundoEmpatado = inserirConcluida("2027-01-02T10:00:00Z");
        // A data de criação não participa da ordenação do histórico.
        banco.update("UPDATE solicitacao SET criada_em = criada_em + interval '1 day' WHERE id = ?", antigo);
        var primeira = consultas.listarTransferencias(dados.origem().bemId(), 0, 2, ator);
        assertEquals(List.of(segundoEmpatado, primeiroEmpatado), ids(primeira));
        assertEquals(3, primeira.totalElementos());
        assertEquals(2, primeira.totalPaginas());
        var segunda = consultas.listarTransferencias(dados.origem().bemId(), 1, 2, ator);
        assertEquals(List.of(antigo), ids(segunda));
        assertEquals(3, segunda.totalElementos());
        var alem = consultas.listarTransferencias(dados.origem().bemId(), Integer.MAX_VALUE, 100, ator);
        assertTrue(alem.conteudo().isEmpty());
        assertEquals(3, alem.totalElementos());
        assertEquals(1, alem.totalPaginas());
    }

    @Test
    void totaisEPaginasContemSomentePedidosVisiveis() {
        Long visivel = inserirConcluida("2027-01-01T10:00:00Z");
        Long oculto = inserirConcluida("2027-01-02T10:00:00Z");
        var externo = prepararOutroSetor();
        banco.update("""
                UPDATE solicitacao SET setor_solicitante_id = ?, setor_origem_id = ?, setor_destino_id = ?
                WHERE id = ?
                """, externo.setorId(), externo.setorId(), externo.setorId(), oculto);
        var pagina = consultas.listarTransferencias(dados.origem().bemId(), 0, 1, ator);
        assertEquals(List.of(visivel), ids(pagina));
        assertEquals(1, pagina.totalElementos());
        assertEquals(1, pagina.totalPaginas());
        var alem = consultas.listarTransferencias(dados.origem().bemId(), 1, 1, ator);
        assertTrue(alem.conteudo().isEmpty());
        assertEquals(1, alem.totalElementos());
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", externo.setorId(), ator.getId());
        assertEquals(List.of(oculto), ids(historico(ator)));
        // Autoria em ambos os pedidos não amplia o acesso do setor atual.
        assertEquals(1, historico(ator).totalElementos());
    }

    @Test
    void bemInativoMantemHistoricoESemTransferenciasVisiveisRetornaPaginaVazia() {
        var vazia = historico(ator);
        assertTrue(vazia.conteudo().isEmpty());
        assertEquals(0, vazia.pagina());
        assertEquals(20, vazia.tamanho());
        assertEquals(0, vazia.totalElementos());
        assertEquals(0, vazia.totalPaginas());
        banco.update("UPDATE bem_patrimonial SET ativo = false WHERE id = ?", dados.origem().bemId());
        assertEquals(vazia, historico(ator));
        Long pedido = inserirConcluida("2027-01-01T10:00:00Z");
        assertEquals(List.of(pedido), ids(historico(ator)));
        var externo = prepararOutroSetor();
        var ocultas = historico(usuario(externo.gestorId()));
        assertTrue(ocultas.conteudo().isEmpty());
        assertEquals(0, ocultas.totalElementos());
        assertEquals(0, ocultas.totalPaginas());
        falha(RECURSO_NAO_ENCONTRADO, () -> consultas.listarTransferencias(Long.MAX_VALUE, 0, 20, ator));
    }

    @Test
    void preservaIdsHistoricosAposMudancaCadastralEPermiteNomesAtuais() {
        Long pedido = inserirConcluida("2027-01-01T10:00:00Z");
        var anterior = historico(ator).conteudo().getFirst();
        var outro = prepararOutroSetor();
        banco.update("""
                UPDATE bem_patrimonial SET setor_id = ?, localizacao_id = ?, responsavel_id = ?, ativo = false
                WHERE id = ?
                """, outro.setorId(), outro.localizacaoId(), outro.gestorId(), dados.origem().bemId());
        banco.update("UPDATE usuario SET setor_id = ?, ativo = false, nome = 'Responsável renomeado' WHERE id = ?",
                outro.setorId(), dados.destino().gestorId());
        banco.update("UPDATE setor SET ativo = false, nome = 'Origem renomeada' WHERE id = ?", dados.origem().setorId());
        banco.update("UPDATE localizacao SET ativo = false WHERE id = ?", dados.destino().localizacaoId());
        var atual = historico(ator).conteudo().getFirst();
        assertEquals(pedido, atual.solicitacaoId());
        assertEquals(anterior.numero(), atual.numero());
        assertEquals(anterior.recebidaEm(), atual.recebidaEm());
        assertEquals(dados.origem().setorId(), atual.origem().setor().id());
        assertEquals(dados.origem().localizacaoId(), atual.origem().localizacao().id());
        assertEquals(dados.origem().gestorId(), atual.origem().responsavel().id());
        assertEquals(dados.destino().setorId(), atual.destino().setor().id());
        assertEquals(dados.destino().localizacaoId(), atual.destino().localizacao().id());
        assertEquals(dados.destino().gestorId(), atual.destino().responsavel().id());
        assertEquals("Origem renomeada", atual.origem().setor().nome());
        assertEquals("Responsável renomeado", atual.destino().responsavel().nome());
        assertEquals(List.of(atual), historico(ator).conteudo());
    }

    @Test
    void transferenciasSucessivasRecemConcluidasAparecemSemReescreverAnterior() {
        var primeira = operacoes.criar(new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), "Transferência de ida"), ator);
        primeira = operacoes.aprovar(primeira.id(), primeira.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
        primeira = operacoes.aprovar(primeira.id(), primeira.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
        primeira = operacoes.enviar(primeira.id(), usuario(dados.origem().gestorId()));
        assertTrue(historico(ator).conteudo().isEmpty());
        primeira = operacoes.receber(primeira.id(), usuario(dados.destino().gestorId()));
        var registroPrimeira = historico(ator).conteudo().getFirst();
        verificarTransferencia(primeira, registroPrimeira);
        var segunda = operacoes.criar(new CriarSolicitacaoRequest(dados.origem().bemId(), dados.origem().setorId(),
                dados.origem().localizacaoId(), dados.origem().gestorId(), "Transferência de volta"), ator);
        segunda = operacoes.aprovar(segunda.id(), segunda.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
        segunda = operacoes.aprovar(segunda.id(), segunda.aprovacoes().getLast().id(), usuario(dados.destino().gestorId()));
        segunda = operacoes.enviar(segunda.id(), usuario(dados.destino().gestorId()));
        assertEquals(List.of(registroPrimeira), historico(ator).conteudo());
        segunda = operacoes.receber(segunda.id(), usuario(dados.origem().gestorId()));
        gerenciador.clear();
        var pagina = historico(ator);
        assertEquals(List.of(segunda.id(), primeira.id()), ids(pagina));
        assertEquals(2, pagina.totalElementos());
        verificarTransferencia(segunda, pagina.conteudo().getFirst());
        assertEquals(registroPrimeira, pagina.conteudo().getLast());
        assertEquals(registroPrimeira.destino(), pagina.conteudo().getFirst().origem());
    }

    @Test
    void validaIdentificadorEPaginacaoSemConfundirBemAusenteComListaVazia() {
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            falha(DADOS_INVALIDOS, () -> consultas.listarTransferencias(id, 0, 20, ator));
        }
        falha(DADOS_INVALIDOS, () -> consultas.listarTransferencias(dados.origem().bemId(), -1, 20, ator));
        for (int tamanho : List.of(-1, 0, 101)) {
            falha(DADOS_INVALIDOS, () -> consultas.listarTransferencias(dados.origem().bemId(), 0, tamanho, ator));
        }
        var invalida = falha(DADOS_INVALIDOS,
                () -> consultas.listarTransferencias(dados.origem().bemId(), -1, 101, ator));
        assertEquals(2, invalida.getErros().size());
        for (int tamanho : List.of(1, 100)) {
            assertEquals(tamanho, consultas.listarTransferencias(dados.origem().bemId(), 0, tamanho, ator).tamanho());
        }
        falha(RECURSO_NAO_ENCONTRADO,
                () -> consultas.listarTransferencias(Long.MAX_VALUE, Integer.MAX_VALUE, 100, ator));
        assertTrue(historico(ator).conteudo().isEmpty());
    }

    @Test
    void recarregaContaPerfilESetorSemConfiarNoObjetoRecebido() {
        inserirConcluida("2027-01-01T10:00:00Z");
        var outro = prepararOutroSetor();
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", outro.setorId(), ator.getId());
        ReflectionTestUtils.setField(ator, "perfil", PerfilUsuario.ADMINISTRADOR);
        assertTrue(historico(ator).conteudo().isEmpty());
        banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR' WHERE id = ?", ator.getId());
        assertEquals(1, historico(ator).totalElementos());
        banco.update("UPDATE usuario SET ativo = false WHERE id = ?", ator.getId());
        falha(NAO_AUTENTICADO, () -> historico(ator));
        Usuario semId = new Usuario("Ator sintético", DadosPatrimoniaisTeste.novoEmail(),
                DadosPatrimoniaisTeste.HASH_SINTETICO, ator.getPerfil(), ator.getSetor());
        Usuario inexistente = new Usuario("Ator inexistente", DadosPatrimoniaisTeste.novoEmail(),
                DadosPatrimoniaisTeste.HASH_SINTETICO, ator.getPerfil(), ator.getSetor());
        ReflectionTestUtils.setField(inexistente, "id", Long.MAX_VALUE);
        for (Usuario invalido : Arrays.asList(null, semId, inexistente)) {
            falha(NAO_AUTENTICADO, () -> historico(invalido));
        }
    }

    @Test
    void respostaPossuiSomenteCamposContratados() {
        assertEquals(List.of("solicitacaoId", "numero", "recebidaEm", "origem", "destino"), campos(TransferenciaBemResponse.class));
        assertEquals(List.of("setor", "localizacao", "responsavel"), campos(ReferenciasTransferenciaResponse.class));
        assertEquals(List.of("conteudo", "pagina", "tamanho", "totalElementos", "totalPaginas"),
                campos(PaginaTransferenciasBemResponse.class));
    }

    private Long inserirConcluida(String recebidaEm) {
        Long pedido = dados.inserirSolicitacao(banco, "CONCLUIDA");
        Timestamp recebimento = Timestamp.from(Instant.parse(recebidaEm));
        banco.update("""
                UPDATE solicitacao SET enviada_em = ?, enviada_por_id = ?, recebida_em = ?, recebida_por_id = ?
                WHERE id = ?
                """, recebimento, dados.origem().gestorId(), recebimento, dados.destino().gestorId(), pedido);
        return pedido;
    }

    private DadosPatrimoniaisTeste prepararOutroSetor() {
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var outro = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        return outro;
    }

    private void verificarTransferencia(SolicitacaoResponse pedido, TransferenciaBemResponse transferencia) {
        assertEquals(pedido.id(), transferencia.solicitacaoId());
        assertEquals(pedido.numero(), transferencia.numero());
        assertEquals(pedido.recebimento().realizadoEm(), transferencia.recebidaEm());
        assertEquals(pedido.origem().setor().id(), transferencia.origem().setor().id());
        assertEquals(pedido.origem().localizacao().id(), transferencia.origem().localizacao().id());
        assertEquals(pedido.origem().responsavel().id(), transferencia.origem().responsavel().id());
        assertEquals(pedido.destino().setor().id(), transferencia.destino().setor().id());
        assertEquals(pedido.destino().localizacao().id(), transferencia.destino().localizacao().id());
        assertEquals(pedido.destino().responsavel().id(), transferencia.destino().responsavel().id());
    }

    private Usuario usuario(Long id) { return usuarios.findById(id).orElseThrow(); }

    private PaginaTransferenciasBemResponse historico(Usuario usuario) {
        return consultas.listarTransferencias(dados.origem().bemId(), null, null, usuario);
    }

    private List<Long> ids(PaginaTransferenciasBemResponse pagina) {
        return pagina.conteudo().stream().map(TransferenciaBemResponse::solicitacaoId).toList();
    }

    private List<String> campos(Class<?> tipo) {
        return Arrays.stream(tipo.getRecordComponents()).map(campo -> campo.getName()).toList();
    }

    private ExcecaoConsultaPatrimonial falha(MotivoFalhaConsultaPatrimonial motivo, Supplier<?> consulta) {
        var excecao = assertThrows(ExcecaoConsultaPatrimonial.class, consulta::get);
        assertEquals(motivo, excecao.getMotivo());
        return excecao;
    }
}
