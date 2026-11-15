# Lume — editor de fotos (Fase 0, base local)

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

Estado: **base Fase 0 local, sem repositório, sem build verificado.**
O Gradle não roda nesta VM (daemon incompatível — ver AGENTS.md). A
primeira compilação real será no CI do repositório, quando ele for
criado. Nada neste README afirma que o app compila ou roda — afirma o
que foi escrito e o que foi verificado estaticamente.

## O que funciona (código escrito, não verificado por build)

- **Projeto Gradle + Compose + Navigation**: telas Biblioteca, Editor,
  Conta e Paywall (shell com a tabela real, sem compra).
- **Photo Picker** do sistema para abrir 1 foto (sem permissão de mídia).
- **Receita paramétrica versionada** (`Recipe` v1, JSON via
  kotlinx-serialization): uma fonte de verdade para preview, exportação,
  presets, histórico e rascunhos.
- **Painéis Luz e Cor** com sliders funcionais sobre a receita:
  exposição, contraste, realces, sombras, brancos, pretos, temperatura,
  matiz, vibração, saturação.
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

**Abordagem da Fase 0: matriz de cor 4x5 compartilhada**
(`core/RecipeColorMatrix.kt`).

- O preview (Compose `ColorFilter.colorMatrix`) e a exportação
  (`android.graphics.ColorMatrix` + Canvas) aplicam a **mesma** matriz,
  gerada da mesma receita. O que se vê é o que sai no arquivo.
- Funciona do minSdk 24 ao atual, sem dependência nova, sem `RenderEffect`
  (API 31+) e sem pipeline OpenGL próprio nesta fase.
- Por que não GPUImage agora: o estudo manda verificar licença e
  manutenção antes de adotar, e o pipeline baseado em View (`AndroidView`)
  adiciona complexidade e risco para a Fase 0. A decisão será revisitada
  na Fase 1, quando HSL/curvas exigirem shader de qualquer forma.

**Limitações da matriz (linear), documentadas no código:**

- Realces/sombras/brancos/pretos são uma **aproximação** (ajuste tonal
  combinado via deslocamento + contraste), não seletivos por faixa tonal.
- Vibrance é saturação mais suave (60%), não vibrance por pixel.
- Temperatura/matiz são ganhos por canal (aproximação de balanço de branco).
- A curva tonal real (LUT 256), o HSL por canal e as máscaras exigem
  shader e são Fase 1/2. A fronteira de troca é `RecipeColorMatrix`:
  quando o motor mudar, preview e exportação continuam idênticos.

## O que é TODO (e por quê)

| TODO | Por quê não está na Fase 0 |
|---|---|
| HSL por canal, curvas, efeitos/detalhe, crop, geometria | Exigem shader/canvas próprios; painéis nascem na Fase 1 sobre a mesma receita |
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
│   ├── Recipe.kt             Receita v1 + interpolação + clamp
│   ├── RecipeColorMatrix.kt  Receita → matriz 4x5 (preview = exportação)
│   ├── RecipeJson.kt         JSON (Room, import/export, futuro sync)
│   ├── Preset.kt             Preset + 6 presets originais embutidos
│   ├── EditHistory.kt        Undo/redo sobre snapshots da receita
│   └── ProPolicy.kt          Tabela Grátis vs Pro + regras nunca-tranca
├── data/
│   ├── local/                Room (presets, rascunhos) + DataStore
│   ├── auth/                 AuthRepository + visitante local
│   └── pro/                  EntitlementRepository + flag de debug
├── imaging/       Decodificação (EXIF), exportação JPEG, compartilhar
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
