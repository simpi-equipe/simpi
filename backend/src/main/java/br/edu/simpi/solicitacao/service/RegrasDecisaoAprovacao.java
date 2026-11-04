package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.enums.DecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.EtapaAprovacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.model.Aprovacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import java.util.Optional;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao.*;

/** Regras puras compartilhadas pela decisão e pelas ações anunciadas na resposta. */
public final class RegrasDecisaoAprovacao {
    private RegrasDecisaoAprovacao() { }

    public static boolean temAcesso(Solicitacao solicitacao, Usuario ator) {
        if (ator == null || ator.getId() == null || !ator.isAtivo()) {
            return false;
        }
        Long setorId = ator.getSetor().getId();
        return ator.getPerfil() == PerfilUsuario.ADMINISTRADOR
                || setorId.equals(solicitacao.getSetorSolicitante().getId())
                || setorId.equals(solicitacao.getSetorOrigem().getId())
                || setorId.equals(solicitacao.getSetorDestino().getId());
    }

    public static Optional<MotivoFalhaDecisaoAprovacao> avaliar(
            Solicitacao solicitacao, Aprovacao aprovacao, Usuario ator) {
        if (ator == null || ator.getId() == null || !ator.isAtivo()) {
            return Optional.of(NAO_AUTENTICADO);
        }
        if (!temAcesso(solicitacao, ator) || ator.getPerfil() != PerfilUsuario.GESTOR
                || !aprovacao.getAprovador().getId().equals(ator.getId())) {
            return Optional.of(ACAO_NAO_PERMITIDA);
        }
        Long setorDaEtapa = aprovacao.getEtapa() == EtapaAprovacao.GESTOR_AREA
                ? solicitacao.getSetorSolicitante().getId() : solicitacao.getSetorOrigem().getId();
        if (!ator.getSetor().getId().equals(setorDaEtapa)) {
            return Optional.of(ACAO_NAO_PERMITIDA);
        }
        if (!aprovacao.getSolicitacao().getId().equals(solicitacao.getId())) {
            return Optional.of(APROVACAO_NAO_ENCONTRADA);
        }
        if (aprovacao.getDecisao() != DecisaoAprovacao.PENDENTE) {
            return Optional.of(ETAPA_JA_DECIDIDA);
        }
        StatusSolicitacao estadoEsperado = aprovacao.getEtapa() == EtapaAprovacao.GESTOR_AREA
                ? StatusSolicitacao.AGUARDANDO_GESTOR_AREA : StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL;
        return solicitacao.getStatus() == estadoEsperado
                ? Optional.empty() : Optional.of(ESTADO_INCOMPATIVEL);
    }
}
