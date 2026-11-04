package br.edu.simpi.solicitacao.enums;

public enum MotivoFalhaDecisaoAprovacao {
    DADOS_INVALIDOS,
    NAO_AUTENTICADO,
    ACAO_NAO_PERMITIDA,
    SOLICITACAO_NAO_ENCONTRADA,
    APROVACAO_NAO_ENCONTRADA,
    ETAPA_JA_DECIDIDA,
    ESTADO_INCOMPATIVEL,
    // Motivo interno; tradução HTTP ainda não definida pela equipe.
    GESTOR_DESIGNADO_INVALIDO
}
