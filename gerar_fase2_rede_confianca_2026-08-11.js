// gerar_fase2_rede_confianca_2026-08-11.js — Documento de design e implementação da Fase 2
// Rede Neural de Confiança do BG (smoothing) — aplicação do multiplicador no SMB
const { Document, Packer, Paragraph, HeadingLevel, Table, TableRow, TableCell, WidthType } = require("docx");
const fs = require("fs");

const th = (t) => new TableCell({ children: [new Paragraph({ text: t, bold: true })] });
const td = (t) => new TableCell({ children: [new Paragraph(String(t))] });
const mkTable = (header, rows) => new Table({
  width: { size: 100, type: WidthType.PERCENTAGE },
  rows: [new TableRow({ children: header.map(th) }), ...rows.map(r => new TableRow({ children: r.map(td) }))],
});

const doc = new Document({
  sections: [{
    properties: {},
    children: [
      new Paragraph({ text: "FASE 2 — Rede Neural de Confiança do BG (smoothing)", heading: HeadingLevel.TITLE }),
      new Paragraph({ text: "Design e implementação | 11/08/2026 | Status: DOCUMENTADO — aguardando aprovação para implementar", heading: HeadingLevel.HEADING_2 }),

      new Paragraph({ text: "1. CONTEXTO", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• Fase 1 (atual): a rede classifica a confiança da leitura (0=OK, 1=UNCERTAIN, 2=BAD) e apenas LOG (bgConfidence no CSV). NÃO afeta o SMB."),
      new Paragraph("• Fase 2 (este documento): aplicar o multiplicador de confiança no cálculo do SMB, com 5 melhorias de design (A-E) descobertas na análise do lag sensor-vs-dedo."),
      new Paragraph("• Fato clínico confirmado (Roche, 11/08): CGM intersticial atrasa 5-20min (estável) e 12-24min (mudanças rápidas — refeição), podendo chegar a 40min. Dedo sobe primeiro; sensor reage depois com delta menor; convergência em 30-40min."),
      new Paragraph("• Validação empírica das janelas de refeição (1.067 ciclos do emu): 60% do padrão de digestão está nas janelas 6-8h/11-13h/17-19h; os 40% restantes são digestões TARDIAS legítimas (09-10h, 14-16h, 20-21h) — por isso o prior temporal deve ser FEATURE (suave), nunca gate rígido."),
      new Paragraph("• Caso real motivador (11/08 07:55): dedo 113 vs sensor 87. Amostra gravada às 07:59 com bg=94 (sensor já subindo), divergência |94-113|=19 → UNCERTAIN. A rede não distingue lag de refeição de ruído — as melhorias A e E resolvem."),

      new Paragraph({ text: "2. ARQUITETURA", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• INPUT_SIZE: 14 → 16 (13 features + trendIndicator + direcaoDivergencia + emJanelaRefeicao)."),
      new Paragraph("• Modelo atual (bg_confidence_model.json): APAGADO em 10/08 para retreino limpo. Sem modelo, classify() retorna 0 (OK) → multiplicador 1.0 → comportamento ATUAL. Implementar a Fase 2 é SEGURO mesmo antes do retreino (fail-safe)."),
      new Paragraph("• CSV de treino: 27 amostras com dateLong (coluna adicionada 10/08). Migração: recalcular emJanelaRefeicao pelo dateLong; direcaoDivergencia=0 para amostras antigas (desconhecida)."),
      new Paragraph("• Fluxo: setData() classifica bgConfidence → applySafetyPrecautions() aplica multiplicador ao smbToGive → roundToPoint05 → log."),

      new Paragraph({ text: "3. MELHORIAS A-E (código Antes/Depois)", heading: HeadingLevel.HEADING_1 }),

      new Paragraph({ text: "MELHORIA A — Feature de direção da divergência (dedo − sensor, com sinal)", bold: true }),
      new Paragraph("Arquivo: AimiBgConfidenceTrainer.kt — FEATURE_COLUMNS, recordSample()"),
      new Paragraph("ANTES:"),
      new Paragraph("  private val FEATURE_COLUMNS = listOf(\"bg\",\"delta\",\"shortAvgDelta\",\"longAvgDelta\",\"saltoAnterior\",\"reversao\",\"idadeSensorMin\",\"compressaoAtiva\",\"iob\",\"cob\",\"tdd7DaysPerHour\",\"isNight\",\"faseSensor\")"),
      new Paragraph("DEPOIS:"),
      new Paragraph("  private val FEATURE_COLUMNS = listOf(..., \"faseSensor\", \"direcaoDivergencia\", \"emJanelaRefeicao\")"),
      new Paragraph("  // recordSample ganha parâmetros: direcaoDivergencia: Double (dedo − sensor, com sinal) e emJanelaRefeicao: Double"),
      new Paragraph("  // line inclui: ..., target.toDouble(), direcaoDivergencia, emJanelaRefeicao, System.currentTimeMillis().toString()"),

      new Paragraph({ text: "MELHORIA B — Nunca BAD em subida sustentada (gabarito + Trava 5)", bold: true }),
      new Paragraph("Arquivos: AimiBgConfidenceTrainer.kt (recordSample — gabarito) e BgConfidenceGuard.kt (applySafety — inferência)"),
      new Paragraph("ANTES (recordSample):"),
      new Paragraph("  val target = when { divergenciaDedo < 15 -> 0; divergenciaDedo < 30 -> 1; else -> 2 }"),
      new Paragraph("DEPOIS (recordSample):"),
      new Paragraph("  val target = when {"),
      new Paragraph("      divergenciaDedo < 15.0 -> 0"),
      new Paragraph("      divergenciaDedo < 30.0 -> 1"),
      new Paragraph("      delta > 1.0 && divergenciaDedo < 40.0 -> 1   // lag fisiológico de subida, NÃO ruído"),
      new Paragraph("      else -> 2"),
      new Paragraph("  }"),
      new Paragraph("ANTES (BgConfidenceGuard.applySafety): travas 1-4 (BG<80 OK, delta<-4 OK, BG>250 BAD→UNCERTAIN, sensor<6h)"),
      new Paragraph("DEPOIS — Trava 5 (inferência, defesa em profundidade):"),
      new Paragraph("  if (delta > 1.0 && tier == 2) tier = 1   // subida real com divergência moderada nunca BAD"),

      new Paragraph({ text: "MELHORIA C — Peso reduzido para amostras de lag (treino)", bold: true }),
      new Paragraph("Arquivo: AimiBgConfidenceTrainer.kt — trainNow() (lógica de peso L344-352)"),
      new Paragraph("ANTES: peso = 3 (ideal BG 70-140 delta<2) | 1 (extremo) | 2 (normal)"),
      new Paragraph("DEPOIS — amostra de lag (target UNCERTAIN + subindo) entra com peso 1:"),
      new Paragraph("  val peso = when {"),
      new Paragraph("      targetVal == 1.0 && raw[3] > 1.0f -> 1   // lag plausível: não ensinar forte que subida = ruído"),
      new Paragraph("      raw[0] in 70.0f..140.0f && abs(raw[3]) < 2.0f -> 3"),
      new Paragraph("      abs(raw[3]) > 5.0f || raw[0] !in 70.0f..140.0f -> 1"),
      new Paragraph("      else -> 2"),
      new Paragraph("  }"),

      new Paragraph({ text: "MELHORIA D (REVISADA 11/08) — Piso 0.8 durante digestão (contexto no multiplicador)", bold: true }),
      new Paragraph("REVISÃO: o usuário NÃO usa COB (não anuncia refeições — low-carb sem anúncio; cob é sempre 0.0)."),
      new Paragraph("Substituído por isDigesting do DigestionDetector (detecção por padrão glicêmico — já validada e ativa no pipeline)."),
      new Paragraph("Arquivo: BgConfidenceGuard.kt — smbMultiplier()"),
      new Paragraph("ANTES: fun smbMultiplier(tier: Int): Double = when(tier){ 2->0.5; 1->0.8; else->1.0 }"),
      new Paragraph("DEPOIS: fun smbMultiplier(tier: Int, isDigesting: Boolean = false, delta: Double = 0.0): Double {"),
      new Paragraph("    val lagEsperado = isDigesting || delta > 1.0"),
      new Paragraph("    return when (tier) { 2 -> if (lagEsperado) 0.8 else 0.5; 1 -> 0.8; else -> 1.0 }"),
      new Paragraph("  }"),
      new Paragraph("Ponto de chamada (applySafetyPrecautions): isDigesting já disponível (L597: val isDigesting = digestionDetector.isActive()) — passar isDigesting em vez de cob."),

      new Paragraph({ text: "MELHORIA E — Feature de janela de refeição (prior temporal suave)", bold: true }),
      new Paragraph("Arquivo: AimiBgConfidenceTrainer.kt (FEATURE_COLUMNS) + DetermineBasalAdapterAIMI.kt (chamador)"),
      new Paragraph("Janelas calibradas com dados reais: café 6-8h | almoço 12-13h (pico 58%) com folga 11-14h | jantar 18-20h com folga 17-21h"),
      new Paragraph("DEPOIS (chamador L1820):"),
      new Paragraph("  val emJanelaRefeicao = when (hourOfDay) { in 6..8, in 11..14, in 17..21 -> 1.0; else -> 0.0 }"),
      new Paragraph("  // capturarSuspensaoAutomatica: direcaoDivergencia=0.0 (sem dedo), emJanelaRefeicao calculada"),

      new Paragraph({ text: "MELHORIA F (ADICIONADA 11/08) — Regra de delta alto (Trava 6 + Trava 7)", bold: true }),
      new Paragraph("Motivo: no verão o usuário observa deltas > 6 frequentes e reação exagerada do sistema. O treino da rede tem 0 exemplos de delta > 6 — não pode aprender esse regime. REGRA é obrigatória."),
      new Paragraph("Calibração com dados reais do NS (30/11/2025 → 31/01/2026, 17.378 deltas):"),
      new Paragraph("  • deltas > +6: 941 (5,41%) — frequente no verão"),
      new Paragraph("  • deltas > +7: 651 (3,75%) — ~11/dia"),
      new Paragraph("  • deltas > +10: 265 (1,52%) — zona de forte suspeita de ruído"),
      new Paragraph("  • maior delta: +58,8 / −59,8 — impossível fisiologicamente (ruído puro de sensor)"),
      new Paragraph("  • |delta| médio: 2,71"),
      new Paragraph("Arquivo: BgConfidenceGuard.kt — applySafety() (regra, NÃO depende da rede):"),
      new Paragraph("  // TRAVA 6 — delta > 10 → leitura é RUÍDO (erro de sensor no calor)"),
      new Paragraph("  if (delta > 10.0) return 2   // BAD → ×0.5 por regra"),
      new Paragraph("  // TRAVA 7 — delta 7-10 → zona de SUSPEITA (limite do fisiológico)"),
      new Paragraph("  if (delta > 7.0 && tier == 0) tier = 1   // UNCERTAIN → ×0.8, nunca BAD (subida real preservada)"),
      new Paragraph("Trade-off: Trava 7 penaliza subidas reais de 7-10 com ×0.8 (não zera — aceitável). Trava 6 (>10) é mais agressiva: risco de punir subida real extrema é menor que reagir a ruído de +30 (o problema observado no verão)."),

      new Paragraph({ text: "4. PONTO DE APLICAÇÃO NO SMB (Fase 2)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("Arquivo: DetermineBasalAdapterAIMI.kt — applySafetyPrecautions() (L583-651), após applyMaxLimits (L646), antes do return (L650)"),
      new Paragraph("DEPOIS (inserir antes do return):"),
      new Paragraph("  // ═══ FASE 2 — BG Confidence Multiplier (Tarciso, 11/Ago/2026) ═══"),
      new Paragraph("  // Aplica o multiplicador de confiança da leitura ao SMB final."),
      new Paragraph("  // Sem modelo (retreino) → tier 0 → ×1.0 → comportamento inalterado (fail-safe)."),
      new Paragraph("  val confMultiplier = BgConfidenceGuard.smbMultiplier(this.bgConfidence, isDigesting, delta)"),
      new Paragraph("  if (confMultiplier < 1.0) {"),
      new Paragraph("      aapsLogger.debug(LTag.APS, \"BG Confidence (tier=$bgConfidence): \" +"),
      new Paragraph("          \"%.2f\".format(smbToGive.toDouble()) + \" -> \" + \"%.2f\".format((smbToGive*confMultiplier).toDouble()) + \" U (×$confMultiplier)\")"),
      new Paragraph("      smbToGive = (smbToGive * confMultiplier.toFloat()).coerceAtLeast(0f)"),
      new Paragraph("  }"),
      new Paragraph("Nota: multiplicador NUNCA aumenta (máx 1.0), BAD não zera (×0.5), piso 0.8 em refeição (D)."),

      new Paragraph({ text: "5. MIGRAÇÃO DO CSV DE TREINO (27 amostras)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• Novo header: bg,...,bgConfidenceTarget,direcaoDivergencia,emJanelaRefeicao,dateLong"),
      new Paragraph("• Amostras existentes: emJanelaRefeicao recalculada pelo dateLong (hora BRT); direcaoDivergencia=0 (desconhecida)."),
      new Paragraph("• Alternativa: descartar amostras antigas (perderia o cluster UNCERTAIN valioso) — NÃO recomendado."),

      new Paragraph({ text: "6. SEGURANÇA (fail-safe e travas)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• Sem modelo → classify() retorna 0 (OK) → ×1.0 → comportamento atual. Implementar antes do retreino é seguro."),
      new Paragraph("• Circuit breaker: 3 falhas consecutivas → ML desligado por 6h (OK fallback)."),
      new Paragraph("• Travas do BgConfidenceGuard: 1) BG<80 sempre OK (nunca mascara hipo); 2) delta<-4 sempre OK (nunca mascara queda); 3) BG>250 BAD→UNCERTAIN; 4) sensor<6h nunca BAD; 5) subida sustentada nunca BAD (Melhoria B); 6) NOVA: delta>10 → BAD por regra (ruído provável, calibrado verão); 7) NOVA: delta 7-10 → UNCERTAIN por regra (zona de suspeita)."),
      new Paragraph("• O multiplicador reduz o SMB no máximo 50% (BAD) — nunca zera, nunca aumenta."),

      new Paragraph({ text: "7. IMPACTO CLÍNICO ESPERADO E RISCOS", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("Esperado: leituras ruidosas (39 constante, reversão de salto, sensor novo oscilando) geram menos SMB — menos doses baseadas em leitura falsa. Subidas reais pós-refeição preservadas (A/B/D/E)."),
      new Paragraph("Riscos: 1) falso UNCERTAIN em leitura boa → SMB ×0.8 por até alguns ciclos (impacto pequeno, temporário); 2) retreino com poucas amostras (27) pode gerar modelo instável → mitigado pelo retreino progressivo (100+ linhas ideais) e travas; 3) alteração de comportamento do SMB → validar no emu antes de qualquer migração (regra 05/Ago)."),
      new Paragraph("Reversão: remover o bloco da Fase 2 em applySafetyPrecautions (1 trecho) — retorna ao comportamento atual imediatamente."),

      new Paragraph({ text: "8. PLANO DE VALIDAÇÃO NO EMULADOR", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("1. Implementar A-E + ponto de aplicação. Verificação ad-hoc (estrutural)."),
      new Paragraph("2. Build no Android Studio (blocker conhecido: Kotlin/JDK23)."),
      new Paragraph("3. Instalar no emu com modelo AUSENTE → confirmar que SMB fica inalterado (×1.0)."),
      new Paragraph("4. Coletar pontas de dedo + capturas automáticas por 2-3 dias → retreino natural (16 inputs)."),
      new Paragraph("5. Comparar emu vs celular (regra 10/Ago): SMB e IOB devem continuar SIMILARES; ruído deve gerar menos SMB no emu."),
      new Paragraph("6. Se comportamento divergir muito: desligar Fase 2 (remover bloco) e reavaliar."),

      new Paragraph({ text: "9. CHECKLIST DE IMPLEMENTAÇÃO", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("[ ] AimiBgConfidenceTrainer.kt: INPUT_SIZE 14→16, FEATURE_COLUMNS +2, recordSample +2 params, capturarSuspensaoAutomatica defaults, trainNow peso (C), recordSample gabarito (B)"),
      new Paragraph("[ ] BgConfidenceGuard.kt: Trava 5 (B), Trava 6+7 (F, delta alto verão), smbMultiplier contexto isDigesting (D)"),
      new Paragraph("[ ] DetermineBasalAdapterAIMI.kt: chamador L1820 +emJanelaRefeicao/direcao, L1438 classify +2 args, applySafetyPrecautions bloco Fase 2"),
      new Paragraph("[ ] Migrar CSV (27 amostras): janela pelo dateLong, direção=0"),
      new Paragraph("[ ] Verificação ad-hoc + build + validação no emu (plano seção 8)"),
      new Paragraph("[ ] BUILD_VERSION incrementar (versão atual 230/05-Ago-2026)"),
    ],
  }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync("/tmp/emu_ns_24h/analise_2026-08-11_fase2_rede_confianca_bg.docx", buf);
  console.log("DOCX gerado: /tmp/emu_ns_24h/analise_2026-08-11_fase2_rede_confianca_bg.docx");
});
