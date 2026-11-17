import { FormEvent, useEffect, useState } from "react";
import { autenticar, limparSessao, consultarSetores, consultarLocalizacoes, consultarUsuarios, consultarBens, consultarBem, consultarTransferencias, consultarSolicitacoes, consultarSolicitacao, criarSolicitacao, executarAcao, Usuario, Setor, Localizacao, UsuarioSelecao, Bem, ResumoSolicitacao, Solicitacao, StatusSolicitacao } from "./servicos/api";

type PaginaAtual = "bens" | "bem" | "nova" | "solicitacoes" | "solicitacao";
const statusTexto: Record<StatusSolicitacao, string> = { AGUARDANDO_GESTOR_AREA: "Aguardando gestor da área", AGUARDANDO_GESTOR_RESPONSAVEL: "Aguardando gestor responsável", APROVADA: "Aprovada", EM_TRANSITO: "Em trânsito", CONCLUIDA: "Concluída", RECUSADA: "Recusada", CANCELADA: "Cancelada" };
const statusCor: Record<StatusSolicitacao, string> = { AGUARDANDO_GESTOR_AREA: "warning", AGUARDANDO_GESTOR_RESPONSAVEL: "warning", APROVADA: "positive", EM_TRANSITO: "info", CONCLUIDA: "neutral", RECUSADA: "danger", CANCELADA: "danger" };
const data = (valor: string) => new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(valor));
const iniciais = (nome: string) => nome.split(" ").slice(0, 2).map((parte) => parte[0]).join("").toUpperCase();

function Botao({ children, tipo = "primary", onClick, desabilitado = false }: { children: React.ReactNode; tipo?: "primary" | "secondary" | "danger" | "ghost"; onClick?: () => void; desabilitado?: boolean }) {
  return <button type="button" className={`btn btn-${tipo}`} onClick={onClick} disabled={desabilitado}>{children}</button>;
}

function Campo({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return <label className="field"><span>{titulo}</span>{children}</label>;
}

function EtiquetaStatus({ status }: { status: StatusSolicitacao }) {
  return <span className={`badge badge-${statusCor[status]}`}><span className="badge-dot" />{statusTexto[status]}</span>;
}

export default function App() {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(false);
  const [pagina, setPagina] = useState<PaginaAtual>("bens");
  const [bens, setBens] = useState<Bem[]>([]);
  const [setores, setSetores] = useState<Setor[]>([]);
  const [localizacoes, setLocalizacoes] = useState<Localizacao[]>([]);
  const [solicitacoes, setSolicitacoes] = useState<ResumoSolicitacao[]>([]);
  const [bemSelecionado, setBemSelecionado] = useState<Bem | null>(null);
  const [detalheBem, setDetalheBem] = useState<Awaited<ReturnType<typeof consultarBem>> | null>(null);
  const [historicoBem, setHistoricoBem] = useState<Awaited<ReturnType<typeof consultarTransferencias>>["conteudo"]>([]);
  const [solicitacaoSelecionada, setSolicitacaoSelecionada] = useState<Solicitacao | null>(null);
  const [busca, setBusca] = useState("");
  const [filtroSetor, setFiltroSetor] = useState("");
  const [filtroLocal, setFiltroLocal] = useState("");
  const [filtroStatus, setFiltroStatus] = useState("");
  const [visao, setVisao] = useState("DA_MINHA_AREA");
  const [setorDestino, setSetorDestino] = useState("");
  const [localDestino, setLocalDestino] = useState("");
  const [responsavelDestino, setResponsavelDestino] = useState("");
  const [responsaveis, setResponsaveis] = useState<UsuarioSelecao[]>([]);
  const [justificativa, setJustificativa] = useState("");
  const [modal, setModal] = useState<{ acao: string; aprovacaoId?: number; titulo: string; descricao: string; exigeMotivo?: boolean } | null>(null);
  const [motivo, setMotivo] = useState("");
  const [mensagem, setMensagem] = useState("");

  async function carregarCadastros() {
    const [listaSetores, listaLocais] = await Promise.all([consultarSetores(), consultarLocalizacoes()]);
    setSetores(listaSetores);
    setLocalizacoes(listaLocais);
  }

  async function carregarBens() {
    const resultado = await consultarBens({ busca: busca || undefined, setorId: filtroSetor ? Number(filtroSetor) : undefined, localizacaoId: filtroLocal ? Number(filtroLocal) : undefined, pagina: 0, tamanho: 100 });
    setBens(resultado.conteudo);
  }

  async function carregarSolicitacoes() {
    const resultado = await consultarSolicitacoes({ busca: busca || undefined, status: filtroStatus ? filtroStatus as StatusSolicitacao : undefined, visao, pagina: 0, tamanho: 100 });
    setSolicitacoes(resultado.conteudo);
  }

  async function entrar(evento: FormEvent) {
    evento.preventDefault();
    setErro("");
    setCarregando(true);
    try {
      const identidade = await autenticar(email, senha);
      setUsuario(identidade);
      await carregarCadastros();
      setPagina("bens");
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível entrar.");
    } finally {
      setCarregando(false);
    }
  }

  function sair() {
    limparSessao();
    setUsuario(null);
    setBens([]);
    setSolicitacoes([]);
    setPagina("bens");
    setSenha("");
  }

  useEffect(() => {
    const expirada = () => { sair(); setErro("Sua sessão expirou. Entre novamente."); };
    window.addEventListener("sessao-expirada", expirada);
    return () => window.removeEventListener("sessao-expirada", expirada);
  }, []);

  useEffect(() => {
    if (!usuario) return;
    const executar = async () => {
      setCarregando(true);
      setErro("");
      try {
        if (pagina === "bens") await carregarBens();
        if (pagina === "solicitacoes") await carregarSolicitacoes();
      } catch (falha) {
        setErro(falha instanceof Error ? falha.message : "Não foi possível carregar os dados.");
      } finally {
        setCarregando(false);
      }
    };
    executar();
  }, [usuario, pagina, busca, filtroSetor, filtroLocal, filtroStatus, visao]);

  async function abrirBem(id: number) {
    setCarregando(true);
    setErro("");
    try {
      const [detalhe, historico] = await Promise.all([consultarBem(id), consultarTransferencias(id)]);
      setDetalheBem(detalhe);
      setBemSelecionado(detalhe);
      setHistoricoBem(historico.conteudo);
      setPagina("bem");
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível consultar o bem.");
    } finally {
      setCarregando(false);
    }
  }

  async function abrirSolicitacao(id: number) {
    setCarregando(true);
    setErro("");
    try {
      setSolicitacaoSelecionada(await consultarSolicitacao(id));
      setPagina("solicitacao");
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível consultar a solicitação.");
    } finally {
      setCarregando(false);
    }
  }

  async function selecionarSetorDestino(valor: string) {
    setSetorDestino(valor);
    setResponsavelDestino("");
    setResponsaveis([]);
    if (!valor) return;
    try {
      setResponsaveis(await consultarUsuarios(Number(valor)));
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível carregar os responsáveis.");
    }
  }

  async function enviarSolicitacao(evento: FormEvent) {
    evento.preventDefault();
    if (!bemSelecionado || !setorDestino || !localDestino || !responsavelDestino || !justificativa.trim()) {
      setErro("Preencha todos os campos da solicitação.");
      return;
    }
    setCarregando(true);
    try {
      const criada = await criarSolicitacao({ bemId: bemSelecionado.id, setorDestinoId: Number(setorDestino), localizacaoDestinoId: Number(localDestino), responsavelDestinoId: Number(responsavelDestino), justificativa: justificativa.trim() });
      setSolicitacaoSelecionada(criada);
      setJustificativa("");
      setMensagem("Solicitação criada com sucesso.");
      setPagina("solicitacao");
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível criar a solicitação.");
    } finally {
      setCarregando(false);
    }
  }

  async function confirmarAcao() {
    if (!solicitacaoSelecionada || !modal) return;
    if (modal.exigeMotivo && !motivo.trim()) {
      setErro("Informe a justificativa para continuar.");
      return;
    }
    setCarregando(true);
    setErro("");
    try {
      const atualizada = await executarAcao(solicitacaoSelecionada.id, modal.acao, modal.aprovacaoId, motivo.trim() || undefined);
      setSolicitacaoSelecionada(atualizada);
      setModal(null);
      setMotivo("");
      setMensagem("Ação realizada com sucesso.");
      if (pagina === "solicitacao") await carregarBens();
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível executar a ação.");
    } finally {
      setCarregando(false);
    }
  }

  if (!usuario) return <div className="login-page"><div className="login-brand"><div className="brand-symbol">S</div><div><strong>SIMPI</strong><span>Sistema Integrado de Movimentação<br />Patrimonial e Inventário</span></div></div><main className="login-card"><div className="eyebrow">ACESSO AO SISTEMA</div><h1>Bem-vindo ao SIMPI</h1><p>Entre com suas credenciais institucionais para continuar.</p>{erro && <div className="alert alert-danger"><strong>Não foi possível entrar</strong><span>{erro}</span></div>}<form onSubmit={entrar}><Campo titulo="E-mail institucional"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="nome@instituicao.edu.br" required /></Campo><Campo titulo="Senha"><input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} placeholder="Digite sua senha" required /></Campo><button className="btn btn-primary" type="submit" disabled={carregando}>{carregando ? "Entrando..." : "Entrar →"}</button></form><div className="prototype-note"><strong>Ambiente acadêmico</strong><span>Utilize uma conta cadastrada no banco de desenvolvimento.</span></div></main><footer>SIMPI • Projeto acadêmico</footer></div>;

  return <div className="app-shell">
    <header className="topbar"><div className="brand compact" onClick={() => setPagina("bens")}><div className="brand-symbol">S</div><div><strong>SIMPI</strong><span>Patrimônio e Inventário</span></div></div><nav><button className={pagina === "bens" || pagina === "bem" || pagina === "nova" ? "active" : ""} onClick={() => { setPagina("bens"); setBusca(""); }}>◇ Bens patrimoniais</button><button className={pagina === "solicitacoes" || pagina === "solicitacao" ? "active" : ""} onClick={() => { setPagina("solicitacoes"); setBusca(""); }}>▤ Solicitações</button></nav><div className="user-menu"><div className="avatar">{iniciais(usuario.nome)}</div><div><strong>{usuario.nome}</strong><span>{usuario.perfil} • {usuario.setor?.nome || "Sem setor"}</span></div><Botao tipo="ghost" onClick={sair}>↪ Sair</Botao></div></header>
    <main className="main">
      {mensagem && <div className="alert alert-success"><span>{mensagem}</span><button onClick={() => setMensagem("")}>×</button></div>}
      {erro && <div className="alert alert-danger"><strong>Atenção</strong><span>{erro}</span><button onClick={() => setErro("")}>×</button></div>}
      {carregando && <div className="inline-note">Carregando informações...</div>}
      {pagina === "bens" && <><PageHead eyebrow="PATRIMÔNIO" title="Bens patrimoniais" description="Consulte os bens cadastrados e acompanhe sua disponibilidade para transferência." /><div className="toolbar"><div className="search"><span>⌕</span><input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Pesquisar por código, plaqueta ou descrição" /></div><select value={filtroSetor} onChange={(e) => setFiltroSetor(e.target.value)}><option value="">Todos os setores</option>{setores.map((setor) => <option key={setor.id} value={setor.id}>{setor.nome}</option>)}</select><select value={filtroLocal} onChange={(e) => setFiltroLocal(e.target.value)}><option value="">Todas as localizações</option>{localizacoes.map((local) => <option key={local.id} value={local.id}>{local.nome}</option>)}</select></div><div className="table-card">{bens.length ? <div className="table-wrap"><table><thead><tr><th>Código / Plaqueta</th><th>Descrição</th><th>Setor atual</th><th>Localização</th><th>Responsável</th><th>Disponibilidade</th><th /></tr></thead><tbody>{bens.map((bem) => <tr key={bem.id}><td className="cell-code"><strong>{bem.codigo}</strong><span>Plq. {bem.plaqueta}</span></td><td className="cell-description"><strong>{bem.descricao}</strong></td><td>{bem.setor.nome}</td><td>{bem.localizacao.nome}</td><td>{bem.responsavel.nome}</td><td className="cell-status"><span className={`availability ${bem.disponivelParaSolicitacao ? "free" : "busy"}`}>{bem.disponivelParaSolicitacao ? "✓ Disponível" : "◷ Indisponível"}</span></td><td className="cell-action"><Botao tipo="secondary" onClick={() => abrirBem(bem.id)}>Ver detalhes</Botao></td></tr>)}</tbody></table></div> : <div className="empty"><h3>Nenhum bem encontrado</h3><p>Altere os filtros ou verifique os cadastros de desenvolvimento.</p></div>}<div className="pagination"><span>{bens.length} bens carregados</span></div></div><p className="footnote">A disponibilidade é calculada pelo servidor e considera solicitações em andamento.</p></>}
      {pagina === "bem" && bemSelecionado && detalheBem && <><button className="back" onClick={() => setPagina("bens")}>← Voltar para bens</button><PageHead eyebrow="DETALHES DO BEM" title={bemSelecionado.codigo} description={bemSelecionado.descricao}><span className={`availability ${bemSelecionado.disponivelParaSolicitacao ? "free" : "busy"}`}>{bemSelecionado.disponivelParaSolicitacao ? "Disponível" : "Indisponível"}</span></PageHead><section className="card"><h2>Informações patrimoniais</h2><div className="detail-grid"><Detail label="Plaqueta" value={bemSelecionado.plaqueta} /><Detail label="Setor" value={bemSelecionado.setor.nome} /><Detail label="Localização" value={bemSelecionado.localizacao.nome} /><Detail label="Responsável" value={bemSelecionado.responsavel.nome} /><Detail label="Situação" value={bemSelecionado.ativo ? "Ativo" : "Inativo"} /></div>{detalheBem.solicitacaoEmAndamento && <p className="inline-note">Solicitação em andamento: {detalheBem.solicitacaoEmAndamento.numero}</p>}<div className="form-actions"><Botao tipo="secondary" onClick={() => setPagina("bens")}>Voltar</Botao><Botao onClick={() => { setSetorDestino(""); setLocalDestino(""); setResponsavelDestino(""); setPagina("nova"); }} desabilitado={!bemSelecionado.disponivelParaSolicitacao || !bemSelecionado.ativo}>Solicitar transferência →</Botao></div></section><section className="card"><h2>Histórico de transferências concluídas</h2>{historicoBem.length ? <div className="timeline">{historicoBem.map((item) => <div key={item.solicitacaoId}><span className="timeline-dot">✓</span><div><strong>{item.numero}</strong><p>{data(item.recebidaEm)}</p><p>{item.origem.setor.nome} → {item.destino.setor.nome}</p></div></div>)}</div> : <p>Nenhuma transferência concluída registrada.</p>}</section></>}
      {pagina === "nova" && bemSelecionado && <><button className="back" onClick={() => setPagina("bem")}>← Voltar para detalhes do bem</button><PageHead eyebrow="NOVA SOLICITAÇÃO" title="Solicitar transferência" description="Informe o destino para movimentação do bem." /><form className="request-form" onSubmit={enviarSolicitacao}><section className="card"><h2>Bem selecionado</h2><div className="asset-summary"><div className="asset-icon">◇</div><div><strong>{bemSelecionado.codigo} — {bemSelecionado.descricao}</strong><span>Plaqueta {bemSelecionado.plaqueta}</span></div></div></section><section className="card"><h2>Origem</h2><div className="detail-grid"><Detail label="Setor" value={bemSelecionado.setor.nome} /><Detail label="Localização" value={bemSelecionado.localizacao.nome} /><Detail label="Responsável" value={bemSelecionado.responsavel.nome} /></div></section><section className="card"><h2>Destino</h2><div className="form-grid"><Campo titulo="Setor de destino"><select value={setorDestino} onChange={(e) => selecionarSetorDestino(e.target.value)} required><option value="">Selecione um setor</option>{setores.map((setor) => <option key={setor.id} value={setor.id}>{setor.nome}</option>)}</select></Campo><Campo titulo="Localização de destino"><select value={localDestino} onChange={(e) => setLocalDestino(e.target.value)} required><option value="">Selecione uma localização</option>{localizacoes.map((local) => <option key={local.id} value={local.id}>{local.nome}</option>)}</select></Campo><Campo titulo="Responsável de destino"><select value={responsavelDestino} onChange={(e) => setResponsavelDestino(e.target.value)} required disabled={!responsaveis.length}><option value="">Selecione um responsável</option>{responsaveis.map((pessoa) => <option key={pessoa.id} value={pessoa.id}>{pessoa.nome}</option>)}</select></Campo><Campo titulo="Justificativa"><textarea value={justificativa} onChange={(e) => setJustificativa(e.target.value)} placeholder="Explique por que a transferência é necessária" required /></Campo></div></section><div className="form-actions"><Botao tipo="secondary" onClick={() => setPagina("bem")}>Voltar</Botao><button className="btn btn-primary" type="submit" disabled={carregando}>Enviar solicitação →</button></div></form></>}
      {pagina === "solicitacoes" && <><PageHead eyebrow="MOVIMENTAÇÕES" title="Solicitações" description="Acompanhe aprovações, envios e recebimentos dos setores em que você participa." /><div className="tabs">{[{ valor: "DA_MINHA_AREA", texto: "Da minha área" }, { valor: "CRIADAS_POR_MIM", texto: "Criadas por mim" }, ...(usuario.perfil === "GESTOR" ? [{ valor: "AGUARDANDO_MINHA_APROVACAO", texto: "Aguardando minha aprovação" }] : []), { valor: "ENVIOS_E_RECEBIMENTOS", texto: "Envios e recebimentos" }, ...(usuario.perfil === "ADMINISTRADOR" ? [{ valor: "TODAS", texto: "Todas" }] : [])].map((opcao) => <button key={opcao.valor} className={visao === opcao.valor ? "active" : ""} onClick={() => setVisao(opcao.valor)}>{opcao.texto}</button>)}</div><div className="toolbar"><div className="search"><span>⌕</span><input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Pesquisar por número ou código do bem" /></div><select value={filtroStatus} onChange={(e) => setFiltroStatus(e.target.value)}><option value="">Todos os status</option>{Object.entries(statusTexto).map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}</select></div><div className="table-card">{solicitacoes.length ? <div className="table-wrap"><table><thead><tr><th>Número / Criação</th><th>Bem</th><th>Solicitante</th><th>Origem → Destino</th><th>Status</th><th /></tr></thead><tbody>{solicitacoes.map((item) => <tr key={item.id}><td className="cell-code"><strong>{item.numero}</strong><span>{data(item.criadaEm)}</span></td><td className="cell-description"><strong>{item.bem.descricao}</strong><span>{item.bem.codigo}</span></td><td>{item.solicitante.nome}<span>{item.solicitante.setor.nome}</span></td><td className="cell-route">{item.setorOrigem.nome} → {item.setorDestino.nome}</td><td><EtiquetaStatus status={item.status} /></td><td className="cell-action"><Botao tipo="secondary" onClick={() => abrirSolicitacao(item.id)}>Ver detalhes</Botao></td></tr>)}</tbody></table></div> : <div className="empty"><h3>Nenhuma solicitação encontrada</h3><p>Não há registros para os filtros selecionados.</p></div>}<div className="pagination"><span>{solicitacoes.length} solicitações carregadas</span></div></div></>}
      {pagina === "solicitacao" && solicitacaoSelecionada && <><button className="back" onClick={() => setPagina("solicitacoes")}>← Voltar para solicitações</button><PageHead eyebrow="DETALHES DA SOLICITAÇÃO" title={solicitacaoSelecionada.numero} description={`Criada em ${data(solicitacaoSelecionada.criadaEm)} por ${solicitacaoSelecionada.solicitante.nome}`}><EtiquetaStatus status={solicitacaoSelecionada.status} /></PageHead><div className="content-grid"><div className="content-main"><section className="card"><h2>Bem solicitado</h2><div className="asset-summary"><div className="asset-icon">◇</div><div><strong>{solicitacaoSelecionada.bem.descricao}</strong><span>{solicitacaoSelecionada.bem.codigo} • Plaqueta {solicitacaoSelecionada.bem.plaqueta}</span></div></div></section><section className="card"><h2>Solicitação</h2><div className="detail-grid"><Detail label="Solicitante" value={solicitacaoSelecionada.solicitante.nome} /><Detail label="Setor solicitante" value={solicitacaoSelecionada.solicitante.setor.nome} /><Detail label="Justificativa" value={solicitacaoSelecionada.justificativa} /></div></section><section className="card"><h2>Origem e destino</h2><div className="compare"><div><span>ORIGEM</span><Detail label="Setor" value={solicitacaoSelecionada.origem.setor.nome} /><Detail label="Localização" value={solicitacaoSelecionada.origem.localizacao.nome} /><Detail label="Responsável" value={solicitacaoSelecionada.origem.responsavel.nome} /></div><div className="compare-arrow">→</div><div><span>DESTINO</span><Detail label="Setor" value={solicitacaoSelecionada.destino.setor.nome} /><Detail label="Localização" value={solicitacaoSelecionada.destino.localizacao.nome} /><Detail label="Responsável" value={solicitacaoSelecionada.destino.responsavel.nome} /></div></div></section><section className="card"><h2>Histórico</h2><div className="timeline">{solicitacaoSelecionada.eventos.map((evento) => <div key={evento.id}><span className="timeline-dot">✓</span><div><strong>{evento.descricao}</strong><p>{evento.autor.nome} • {data(evento.ocorridoEm)}</p></div></div>)}</div></section><section className="card"><h2>Aprovações</h2>{solicitacaoSelecionada.aprovacoes.map((aprovacao) => <div className="detail-grid" key={aprovacao.id}><Detail label={aprovacao.etapa === "GESTOR_AREA" ? "Gestor da área" : "Gestor responsável"} value={aprovacao.aprovador.nome} /><Detail label="Decisão" value={aprovacao.decisao} />{aprovacao.justificativa && <Detail label="Justificativa" value={aprovacao.justificativa} />}</div>)}</section></div><aside><section className="card action-card"><h2>Ações disponíveis</h2>{solicitacaoSelecionada.acoesDisponiveis.includes("APROVAR") && <>{solicitacaoSelecionada.aprovacoes.filter((a) => a.decisao === "PENDENTE").map((a) => <Botao key={a.id} onClick={() => setModal({ acao: "APROVAR", aprovacaoId: a.id, titulo: "Aprovar solicitação?", descricao: "Confirme a aprovação desta etapa." })}>Aprovar</Botao>)}</>}{solicitacaoSelecionada.acoesDisponiveis.includes("RECUSAR") && solicitacaoSelecionada.aprovacoes.filter((a) => a.decisao === "PENDENTE").map((a) => <Botao key={a.id} tipo="danger" onClick={() => setModal({ acao: "RECUSAR", aprovacaoId: a.id, titulo: "Recusar solicitação?", descricao: "A recusa encerra a solicitação.", exigeMotivo: true })}>Recusar</Botao>)}{solicitacaoSelecionada.acoesDisponiveis.includes("CANCELAR") && <Botao tipo="secondary" onClick={() => setModal({ acao: "CANCELAR", titulo: "Cancelar solicitação?", descricao: "Informe o motivo do cancelamento.", exigeMotivo: true })}>Cancelar solicitação</Botao>}{solicitacaoSelecionada.acoesDisponiveis.includes("ENVIAR") && <Botao onClick={() => setModal({ acao: "ENVIAR", titulo: "Registrar envio?", descricao: "Confirme que o bem foi enviado." })}>Registrar envio</Botao>}{solicitacaoSelecionada.acoesDisponiveis.includes("RECEBER") && <Botao onClick={() => setModal({ acao: "RECEBER", titulo: "Confirmar recebimento?", descricao: "Confirme o recebimento do bem." })}>Confirmar recebimento</Botao>}{!solicitacaoSelecionada.acoesDisponiveis.length && <div className="no-actions"><strong>Nenhuma ação disponível</strong><span>Esta solicitação está disponível para consulta.</span></div>}</section></aside></div></>}
    </main>
    {modal && <div className="modal-backdrop"><div className="modal" role="dialog" aria-modal="true"><div className="modal-mark">!</div><h2>{modal.titulo}</h2><p>{modal.descricao}</p>{modal.exigeMotivo && <Campo titulo="Justificativa"><textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} required /></Campo>}<div className="modal-actions"><Botao tipo="secondary" onClick={() => { setModal(null); setMotivo(""); }}>Voltar</Botao><Botao tipo={modal.acao === "RECUSAR" || modal.acao === "CANCELAR" ? "danger" : "primary"} onClick={confirmarAcao} desabilitado={carregando}>{carregando ? "Processando..." : "Confirmar"}</Botao></div></div></div>}
  </div>;
}

function PageHead({ eyebrow, title, description, children }: { eyebrow: string; title: string; description: string; children?: React.ReactNode }) {
  return <div className="page-head"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{description}</p></div>{children}</div>;
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div className="detail"><span>{label}</span><strong>{value}</strong></div>;
}
