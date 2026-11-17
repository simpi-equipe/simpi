const enderecoApi = import.meta.env.VITE_API_URL || "http://localhost:8080";
let tokenAcesso: string | null = null;

export type Usuario = { id: number; nome: string; email: string; perfil: "COMUM" | "GESTOR" | "ADMINISTRADOR"; setor: Referencia | null };
export type Referencia = { id: number; nome: string };
export type Setor = { id: number; codigo: string; nome: string };
export type Localizacao = { id: number; codigo: string; nome: string };
export type UsuarioSelecao = { id: number; nome: string; setor: Referencia };
export type Bem = { id: number; codigo: string; plaqueta: string; descricao: string; setor: Referencia; localizacao: Referencia; responsavel: Referencia; ativo: boolean; disponivelParaSolicitacao: boolean };
export type Pagina<T> = { conteudo: T[]; pagina: number; tamanho: number; totalElementos: number; totalPaginas: number };
export type ResumoSolicitacao = { id: number; numero: string; criadaEm: string; status: StatusSolicitacao; bem: { id: number; codigo: string; descricao: string }; solicitante: { id: number; nome: string; setor: Referencia }; setorOrigem: Referencia; setorDestino: Referencia };
export type StatusSolicitacao = "AGUARDANDO_GESTOR_AREA" | "AGUARDANDO_GESTOR_RESPONSAVEL" | "APROVADA" | "EM_TRANSITO" | "CONCLUIDA" | "RECUSADA" | "CANCELADA";
export type Solicitacao = { id: number; numero: string; status: StatusSolicitacao; criadaEm: string; justificativa: string; bem: { id: number; codigo: string; plaqueta: string; descricao: string }; solicitante: { id: number; nome: string; setor: Referencia }; origem: { setor: Referencia; localizacao: Referencia; responsavel: Referencia }; destino: { setor: Referencia; localizacao: Referencia; responsavel: Referencia }; aprovacoes: { id: number; etapa: "GESTOR_AREA" | "GESTOR_RESPONSAVEL"; aprovador: Referencia; decisao: "PENDENTE" | "APROVADA" | "RECUSADA"; justificativa: string | null; decididaEm: string | null }[]; envio: { realizadoEm: string; realizadoPor: Referencia } | null; recebimento: { realizadoEm: string; realizadoPor: Referencia } | null; eventos: { id: number; tipo: string; autor: Referencia; statusAnterior: StatusSolicitacao | null; statusNovo: StatusSolicitacao; descricao: string; ocorridoEm: string }[]; acoesDisponiveis: string[] };

async function requisitar<T>(rota: string, opcoes: RequestInit = {}): Promise<T> {
  const cabecalhos = new Headers(opcoes.headers);
  if (opcoes.body) cabecalhos.set("Content-Type", "application/json");
  if (tokenAcesso) cabecalhos.set("Authorization", `Bearer ${tokenAcesso}`);
  const resposta = await fetch(`${enderecoApi}${rota}`, { ...opcoes, headers: cabecalhos });
  if (resposta.status === 401) {
    tokenAcesso = null;
    window.dispatchEvent(new Event("sessao-expirada"));
  }
  if (!resposta.ok) {
    const erro = await resposta.json().catch(() => null) as { mensagem?: string; codigo?: string } | null;
    throw new Error(erro?.mensagem || erro?.codigo || `Erro ${resposta.status} ao comunicar com o servidor.`);
  }
  if (resposta.status === 204) return undefined as T;
  return resposta.json() as Promise<T>;
}

function consultaUrl(valores: Record<string, string | number | boolean | undefined>) {
  const parametros = new URLSearchParams();
  Object.entries(valores).forEach(([chave, valor]) => {
    if (valor !== undefined && valor !== "") parametros.set(chave, String(valor));
  });
  return parametros.toString();
}

export async function autenticar(email: string, senha: string) {
  const resposta = await requisitar<{ accessToken: string; tokenType: string; expiresIn: number; usuario: Usuario }>("/api/auth/login", { method: "POST", body: JSON.stringify({ email: email.trim(), senha }) });
  tokenAcesso = resposta.accessToken;
  return resposta.usuario;
}

export function limparSessao() { tokenAcesso = null; }
export function consultarUsuarioAtual() { return requisitar<Usuario>("/api/auth/me"); }
export function consultarSetores() { return requisitar<Setor[]>("/api/setores"); }
export function consultarLocalizacoes() { return requisitar<Localizacao[]>("/api/localizacoes"); }
export function consultarUsuarios(setorId: number) { return requisitar<UsuarioSelecao[]>(`/api/usuarios?setorId=${setorId}`); }
export function consultarBens(filtros: { busca?: string; setorId?: number; localizacaoId?: number; disponivelParaSolicitacao?: boolean; pagina?: number; tamanho?: number }) { return requisitar<Pagina<Bem>>(`/api/bens?${consultaUrl(filtros)}`); }
export function consultarBem(id: number) { return requisitar<Bem & { solicitacaoEmAndamento: { id: number; numero: string } | null }>(`/api/bens/${id}`); }
export function consultarTransferencias(id: number) { return requisitar<Pagina<{ solicitacaoId: number; numero: string; recebidaEm: string; origem: { setor: Referencia; localizacao: Referencia; responsavel: Referencia }; destino: { setor: Referencia; localizacao: Referencia; responsavel: Referencia } }>>(`/api/bens/${id}/transferencias`); }
export function consultarSolicitacoes(filtros: { busca?: string; status?: StatusSolicitacao; visao?: string; pagina?: number; tamanho?: number }) { return requisitar<Pagina<ResumoSolicitacao>>(`/api/solicitacoes?${consultaUrl(filtros)}`); }
export function consultarSolicitacao(id: number) { return requisitar<Solicitacao>(`/api/solicitacoes/${id}`); }
export function criarSolicitacao(dados: { bemId: number; setorDestinoId: number; localizacaoDestinoId: number; responsavelDestinoId: number; justificativa: string }) { return requisitar<Solicitacao>("/api/solicitacoes", { method: "POST", body: JSON.stringify(dados) }); }
export function executarAcao(id: number, acao: string, aprovacaoId?: number, justificativa?: string) {
  let rota = `/api/solicitacoes/${id}`;
  let corpo: string | undefined;
  if (acao === "APROVAR" || acao === "RECUSAR") {
    if (!aprovacaoId) throw new Error("A etapa de aprovação não foi encontrada.");
    rota += `/aprovacoes/${aprovacaoId}/${acao === "APROVAR" ? "aprovar" : "recusar"}`;
    if (acao === "RECUSAR") corpo = JSON.stringify({ justificativa });
  } else if (acao === "CANCELAR") {
    rota += "/cancelar";
    corpo = JSON.stringify({ motivo: justificativa });
  } else if (acao === "ENVIAR") rota += "/enviar";
  else if (acao === "RECEBER") rota += "/receber";
  return requisitar<Solicitacao>(rota, { method: "POST", body: corpo });
}
