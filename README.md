# Lume — editor de fotos (Fase 1, motor + geometria)

> **Nome provisório.** "Lume" e o `applicationId` `app.lume` vieram do
> protótipo visual e **não são decisão de marca**. Decidir nome final,
> pacote e identidade visual ANTES de publicar na Play Store — o
> `applicationId` não pode mudar depois de publicado.

App Android nativo (Kotlin + Compose, minSdk 24), construído a partir de
dois documentos internos:

- Análise de mecanismos do editor de referência
  (`~/workspace/polish/RELATORIO-ANALISE.md`, §7) — **mecanismos apenas**.
  Nenhum código, asset, LUT, shader, texto ou visual de concorrente foi
  copiado. Tudo aqui é original.
- Estudo avançado estilo Lightroom
  (`~/workspace/research_notes/lightroom-photo-editor-study-20261114-0124/report.md`,
  §§4–7): tabela Grátis vs Pro, contas, arquitetura de receita
  paramétrica, roteiro por fases e diferenciais P0.

Estado: **Fase 1 em construção, repositório `rennervdprog/lume`, CI
verde até o motor (0.3.0).** O Gradle não roda nesta VM (daemon
incompatível — ver AGENTS.md); a compilação real é o CI do repositório
(workflow `ci.yml`: assembleDebug + testes unitários). Nada neste
README afirma que o app roda — afirma o que foi escrito e o que foi
verificado (estático + CI + teste no aparelho).

## O que funciona (código escrito, não verificado por build)

- **Projeto Gradle + Compose + Navigation**: telas Biblioteca, Editor,
  Conta e Paywall (shell com a tabela real, sem compra).
- **Photo Picker** do sistema para abrir 1 foto (sem permissão de mídia).
- **Receita paramétrica versionada** (`Recipe` v2, JSON via
  kotlinx-serialization): uma fonte de verdade para preview, exportação,
  presets, histórico e rascunhos. JSON v1 decodifica com defaults v2.
- **Motor GLSL próprio** (um passe só, EGL offscreen): faixas tonais
  seletivas, HSL 8 canais, curva tonal (LUT 256), efeitos — com
  fallback para a matriz na CPU se o GL falhar.
- **Geometria** (`Geometry`, separada da receita): giro 90°, espelhos,
  endireitar ±10° (corta o retângulo inscrito, sem cantos vazios) e
  corte com moldura arrastável + proporções prontas. Aplica-se DEPOIS
  da cor, igual no preview e na exportação. Vive só na sessão
  (rascunhos guardam só a receita — persistência chega com migração).
- **Editor redesenhado** (ergonomia aprovada em preview): foto grande
  de borda a borda, uma fileira de ferramentas, sliders só da
  ferramenta ativa, X/✓ por ferramenta (descarta/confirma e recolhe),
  segurar para comparar + botão de dividir, histograma pequeno só em
  Luz, Simples/Pro como segmento pequeno no painel Luz, Copiar edição
  no Histórico.
- **Painéis Luz, Cor, Efeitos e Presets** sobre a receita: sliders de
  luz/cor, HSL por cor (4 canais grátis, 8 Pro), chips de curva tonal,
  claridade/vinheta/grão/desbotado, trilho de ferramentas, histograma,
  comparar segurar/dividir, pilha de edição, lote por copiar/colar.
- **Presets como receitas**: 6 presets iniciais originais, criar/salvar/
  aplicar, intensidade 0–100%, segurar para comparar (antes/depois),
  importar/exportar JSON.
- **Desfazer/refazer + histórico** sobre snapshots da receita (nunca
  bitmap por etapa). Um gesto de slider = uma entrada.
- **Exportar JPEG em resolução total**, sem marca d'água, sem limite,
  para a galeria (pasta "Lume"), com compartilhar via share sheet.
- **Room local**: presets do usuário e rascunhos (URI + receita).
- **Conta**: abstração `AuthRepository` + visitante local funcional.
  O app abre, edita e exporta sem conta. Google/e-mail são TODOs.
- **Pro**: abstração `EntitlementRepository` + flag local de debug. A
  tabela Grátis vs Pro do estudo §4.4 está codificada em `ProPolicy`
  como feature flags, com teste unitário das regras "nunca tranca".
- **Tema**: escuro profissional, 1 destaque azul `#31A8FF`, neutros,
  escala de espaçamento 4/8/12/16/24/32, Material 3. Nunca âmbar/amarelo.
  Sem preto puro.

## Decisão de renderização (e suas limitações honestas)

**Abordagem da Fase 1: shader GLSL próprio, um passe só**
(`imaging/GlPhotoRenderer.kt`, via `imaging/GlEngine.kt`).

- O preview e a exportação renderizam a **mesma** receita no **mesmo**
  shader (EGL pbuffer offscreen, OpenGL ES 2.0): claridade → exposição →
  balanço de branco → faixas tonais seletivas → contraste → vibração/
  saturação → HSL 8 canais → curvas (LUT 256, master + RGB) → desbotado
  → vinheta → grão. O que se vê é o que sai no arquivo.
- A matriz de cor 4x5 (`core/RecipeColorMatrix.kt`) virou a **rota de
  fallback**: se o GL falhar (sem EGL, foto acima do limite de textura
  da GPU), preview e exportação caem para a matriz na CPU — a edição
  continua funcionando, sem os ajustes v2 (HSL/curva/efeitos). A tira
  de presets e as amostras da Biblioteca usam a matriz direto
  (aproximação rápida dos looks).
- Por que shader próprio e não GPUImage: sem dependência nova, sem
  pipeline baseado em View (`AndroidView`), e o controle total da ordem
  dos estágios — que é o contrato de portabilidade dos presets.

**Limitações honestas, documentadas no código:**

- A claridade usa a média 3x3 da fonte (borrar os vizinhos já
  processados exigiria 9 avaliações do pipeline por pixel).
- Fotos acima de `GL_MAX_TEXTURE_SIZE` caem para a matriz (sem tiles).
- Temperatura/matiz seguem ganhos por canal (aproximação de balanço de
  branco, igual à matriz — os looks da Fase 0 carregam iguais).

## O que é TODO (e por quê)

| TODO | Por quê não está nesta entrega |
|---|---|
| Conta-gotas HSL, QR por câmera, mais presets | Próximas entregas da Fase 1, sobre este motor |
| Persistência da geometria em rascunhos | Exige campo novo no banco (destrutivo pré-lançamento é aceitável); hoje a geometria vive só na sessão |
| Máscaras, remoção, remoção de fundo, IA on-device | Fase 2/3 do roteiro; flags Pro já existem |
| Lote, RAW/DNG, câmera in-app | Fase 1/2; flags Pro já existem |
| Login Google / e-mail + sync + migração visitante→conta | Exige projeto backend próprio (decisão pendente); interfaces prontas |
| Play Billing + verificação no servidor + produtos | Exige conta Play + produtos criados; paywall é shell honesto, sem compra falsa |
| Exclusão de conta no servidor + link web + política de privacidade | Exigem backend + páginas públicas; fluxo local existe |
| Preservar EXIF na exportação, HEIC/PNG/TIFF | Fase 1; hoje sai JPEG sem EXIF original |
| Migrações Room explícitas | `fallbackToDestructiveMigration` é aceitável só pré-lançamento; remover antes da Play |
| Testes de UI / screenshot | Fase 1, com CI; hoje só testes unitários puros |
| Nome, marca, ícone final, loja de criadores | Decisões de produto; nada disso é Fase 0 |

## Arquitetura

```
app.lume
├── core/          Puro (sem Android, testável em JVM)
│   ├── Recipe.kt             Receita v2 + interpolação + clamp
│   ├── HslChannels.kt        8 canais HSL + centros + normalização
│   ├── ToneCurve.kt          Curva tonal (master + RGB) + chips
│   ├── ToneCurveLut.kt       Curva → LUT 256 / RGBA do shader
│   ├── RecipeColorMatrix.kt  Receita → matriz 4x5 (fallback do motor)
│   ├── RecipeJson.kt         JSON (Room, import/export, futuro sync)
│   ├── Preset.kt             Preset + 6 presets originais embutidos
│   ├── EditHistory.kt        Undo/redo sobre snapshots da receita
│   ├── Geometry.kt           Geometria (giro/espelho/endireitar/corte) + matemática pura
│   └── ProPolicy.kt          Tabela Grátis vs Pro + regras nunca-tranca
├── data/
│   ├── local/                Room (presets, rascunhos) + DataStore
│   ├── auth/                 AuthRepository + visitante local
│   └── pro/                  EntitlementRepository + flag de debug
├── imaging/
│   ├── GlPhotoRenderer.kt    EGL + shader (motor, um passe só)
│   ├── GlEngine.kt           Thread GL + fallback seguro
│   ├── GeometryTransform.kt  Geometria sobre o bitmap (matriz + corte)
│   ├── BitmapLoader.kt       Decodificação com EXIF
│   ├── PhotoExporter.kt      Exportação JPEG (motor + fallback)
│   ├── Histogram.kt          Histograma do bitmap renderizado
│   ├── PresetQr.kt           QR do preset (ZXing)
│   └── ShareHelper.kt        Compartilhar via share sheet
├── di/            AppContainer (composição manual, sem framework DI)
└── ui/            theme, navigation, library, editor, account, paywall
```

Regras duras aplicadas (AGENTS.md):

- minSdk 24: **nunca `java.time`** (datas com `SimpleDateFormat` + catch).
- Sem `!!` em dados de JSON/arquivo/rede.
- Ícones sempre `Icons.Filled.Nome`.
- Cores via `colorScheme`, tipografia via papéis M3.
- Modifiers: layout → clip → background → clickable → padding.
- Nenhuma permissão no manifest (Photo Picker + MediaStore bastam).
- Nenhuma chamada de rede, nenhum segredo, nenhuma chave no código.

## Verificação feita (estática — sem build)

- `check_braces.py` em todos os arquivos `.kt` (balanceamento).
- Grep de imports: cada símbolo novo conferido contra seus imports;
  nenhum import `androidx.compose.material` (M2) em arquivo M3;
  nenhum `java.time`, nenhum `!!`.
- Inventário de arquivos + revisão de cada arquivo escrito.
- Testes unitários escritos, **não executados** (sem Gradle nesta VM).

## Como verificar via CI (quando houver repo)

1. Criar o repositório GitHub (decisão pendente: nome do repo).
2. Subir este diretório como commit inicial (sem segredos — não há nenhum).
3. Adicionar workflow `build-debug-apk.yml` (mesmo padrão dos apps do
   Renner: `workflow_dispatch`, JDK 17, `./gradlew :app:assembleDebug`
   + `:app:testDebugUnitTest`).
4. Disparar o workflow e ler o resultado da run. Se falhar: baixar os
   logs primeiro, ler a linha `e: arquivo.kt:linha:coluna`, corrigir o
   que ela diz. Nunca chutar a causa.
5. Só afirmar que compila/funciona após o CI verde + teste no aparelho.

## Decisões pendentes (do Renner, antes de publicar)

1. Nome final da marca + `applicationId` final + ícone final.
2. Criação do repositório GitHub + workflow de CI.
3. Projeto backend próprio (Firebase ou Supabase — nunca reutilizar o
   backend de outro app) para auth/sync/verificação de compras.
4. Produtos Play Billing (mensal/anual/vitalício) + preços validados em
   teste — os valores do estudo são hipótese, não promessa.
5. Política de privacidade em PT-BR + página web de exclusão de conta.
6. Coleções de presets P0 testadas em pele/luz brasileiras (Fase 1).
