// gerar_proposta_arquivada_rebote_2026-08-11.js — Arquivamento da proposta de proteção de rebote pós-hipo
const { Document, Packer, Paragraph, HeadingLevel, Table, TableRow, TableCell, WidthType } = require("docx");
const fs = require("fs");

const th = (t) => new TableCell({ children: [new Paragraph({ text: t, bold: true })] });
const td = (t) => new TableCell({ children: [new Paragraph(String(t))] });
const mkTable = (header, rows) => new Table({
  width: { size: 100, type: WidthType.PERCENTAGE },
  rows: [new TableRow({ children: header.map(th) }), ...rows.map(r => new TableRow({ children: r.map(td) }))],
});

const t1 = mkTable(["Ciclo", "BG emu", "delta", "IOB", "pred (ML)", "maxSMB (teto)", "smbGiven"], [
  ["21:03", "130", "+1,67", "2,85", "2,08", "1,68", "0,20"],
  ["21:13", "133", "+1,33", "3,02", "2,16", "1,55", "0,05"],
  ["21:23", "136", "+1,33", "2,98", "2,20", "1,59", "0,40"],
  ["21:38", "142", "+2,33", "3,31", "2,29", "2,17", "0,25"],
  ["21:48", "145", "+1,67", "3,46", "2,31", "1,89", "0,75"],
]);

const t2 = mkTable(["Proteção", "Condição", "Por que não atuou às 07:45"], [
  ["bounce P1 (L908)", "lowestBgUltimaHora <= 70", "Nadir foi 79 — acima do limiar"],
  ["hypoRecovery (L856)", "lowestBg <= 70 E isNight", "Nadir 79 + já é dia (07:45)"],
  ["alarmeLowRecente (L1946)", "alarme há < 30min", "Alarme ~06:38; às 07:45 passaram 67min"],
  ["Rate limiter noturno (L927)", "isNight 22-5", "07:45 é dia"],
  ["nearMiss (L907)", "shortAvgDelta < -1", "Rebote tem delta positivo"],
]);

const t3 = mkTable(["07:45–07:59 (rebote)", "Celular", "Emulador"], [
  ["07:45", "0,40U", "0,50U"],
  ["07:50", "0,30U", "0,45U"],
  ["07:59", "0,40U", "0,30U"],
  ["Total", "1,10U", "1,25U"],
]);

const doc = new Document({
  sections: [{
    properties: {},
    children: [
      new Paragraph({ text: "Proposta Arquivada — Proteção de Rebote Pós-Hipo (nadir 07h)", heading: HeadingLevel.TITLE }),
      new Paragraph({ text: "Arquivado em 11/08/2026 | Status: NÃO IMPLEMENTADA — primeira opção de ajuste futuro", heading: HeadingLevel.HEADING_2 }),

      new Paragraph({ text: "1. OBSERVAÇÃO DE ARQUIVAMENTO (decisão do usuário)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("Arquivada como PRIMEIRA OPÇÃO caso se observe a necessidade de MAIOR AGRESSIVIDADE do sistema no futuro. Se o algoritmo for ajustado para dar mais SMB (mais assertividade), esta proteção de rebote pós-hipo deve ser a primeira salvaguarda a ser ativada junto — ela garante que o aumento de agressividade não produza segunda queda no padrão de recuperação fisiológica pós-hipo."),
      new Paragraph("Motivo do arquivamento: com dados atualizados (07:00-10:28 de 11/08), o comportamento emu × celular foi SIMILAR (15 doses/4,50U celular vs 14 doses/4,45U emu = 99% do volume). O celular TAMBÉM doseou no rebote (1,10U vs 1,25U do emu) — a divergência inicialmente reportada (0,70U vs 1,25U) era artefato de atraso de sincronização do Nightscout (SMB das 07:59 ainda não havia chegado à coleta das 08:00)."),

      new Paragraph({ text: "2. CONTEXTO DA DESCOBERTA", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("Comparativo Emu × Celular de 24h (10/08 08:00 → 11/08 08:00 BRT) revelou que, às 07h, o emu doseou 1,25U em 3 SMBs (07:45/07:50/07:59) num REBOTE pós-hipo (nadir real 79 às 07:30-07:35; BG subindo 81→85→87→94). O comportamento durante a queda foi correto (maxSMB 0,10, SMB 0,00 de 06:23 a 07:40). O problema potencial: tratar o rebote como hiper."),

      new Paragraph({ text: "3. POR QUE NENHUMA TRAVA DISPAROU (o gap)", heading: HeadingLevel.HEADING_1 }),
      t2,

      new Paragraph({ text: "4. EXPLICAÇÃO DETALHADA (mecanismo)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• Durante a queda (06:23-07:40): BG 99→79, delta -2,7 a -4,7 → maxSMB colapsou para 0,10 → SMB bloqueado. COMPORTAMENTO CORRETO."),
      new Paragraph("• No rebote (07:45-07:59): BG 81→94, delta +1,3 a +4,0 → maxSMB subiu (0,99→2,44) e o algoritmo interpretou como hiper começando → 3 SMBs (0,50+0,45+0,30 = 1,25U)."),
      new Paragraph("• O bounce P1 e o hypoRecovery só reagem a nadir ≤ 70; o lockout pós-alarme (30min) expirou às ~07:08; o rate limiter noturno não atua de dia. Resultado: rebote com nadir 71-90 fica sem proteção dedicada."),
      new Paragraph("• Risco teórico: SMB no rebote pós-hipo pode causar SEGUNDA QUEDA (padrão do evento 04:40 de 23/Jul). NÃO se materializou em 11/08 (BG real subiu suave 84→104→112 sem nova hipo), mas o padrão é conhecido."),

      new Paragraph({ text: "5. CORREÇÃO DA ANÁLISE INICIAL (dados atualizados)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("Na análise de 24h, reportou-se celular 0,70U às 07h. Com coleta fresca (11/08 10:28), o SMB das 07:59 do celular (0,40U) havia chegado ao NS com atraso — o total real do celular às 07h foi 1,10U (3 doses)."),
      t3,
      new Paragraph("Conclusão da correção: o emu NÃO divergiu do celular no rebote (1,25 vs 1,10U — diferença 0,15U). O comportamento foi similar, confirmando a percepção do usuário."),

      new Paragraph({ text: "6. PROPOSTA ARQUIVADA (Opções — para ativação futura)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph({ text: "Opção A — Alargar limiar do Bounce P1 (recomendada, cirúrgica)", bold: true }),
      new Paragraph("Arquivo: DetermineBasalAdapterAIMI.kt, applySpecificAdjustments() L907-912"),
      new Paragraph("ANTES:  val bounce = lowestBgUltimaHora <= 70.0 && bg < 160.0 && delta > 0"),
      new Paragraph("DEPOIS: val bounce = lowestBgUltimaHora <= 85.0 && bg < 160.0 && delta > 0"),
      new Paragraph("Impacto (caso real 07:45-07:59): 1,25U → ~0,60U (×0,5 soft guard). Trade-off: bounce ativo até 1h após qualquer BG ≤ 85 na última hora — desejável (subida pós-hipo leve = rebote)."),
      new Paragraph({ text: "Opção B — Estender lockout pós-alarme low", bold: true }),
      new Paragraph("Arquivo: DetermineBasalAdapterAIMI.kt, alarmeLowRecente() L1953"),
      new Paragraph("ANTES:  val menosDe30Min = minutosDesdeAlarme < 30"),
      new Paragraph("DEPOIS: val menosDe30Min = minutosDesdeAlarme < 90"),
      new Paragraph("Impacto: alarme 06:38 → lockout até ~08:08; doses 07:45-07:59 zeradas (0,00U vs celular 0,70U — mais conservador). Condição bg < 110 limita o dano. NOTA: 60min não cobre o caso (expira 07:38); só 90min cobre."),
      new Paragraph({ text: "Opção C — Combinada (defesa em profundidade)", bold: true }),
      new Paragraph("A + B: bounce ≤ 85 reduz (×0,5) o rebote sem alarme; lockout 90min zera o rebote logo após alarme. Cobre por dois caminhos independentes."),
      new Paragraph({ text: "Variante A2 (mais forte, não recomendada como padrão)", bold: true }),
      new Paragraph("Estender hypoRecovery (L856) para lowestBg <= 85 && bg < 96 && delta > 0 && iob <= 0.9 SEM isNight — corta SMB por completo no rebote diurno. Bloqueia subidas legítimas de BG 85-96 o dia todo; só se hipos diurnas forem frequentes."),

      new Paragraph({ text: "7. PRÓXIMOS PASSOS (se ativada no futuro)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("1. Implementar a opção escolhida no DetermineBasalAdapterAIMI.kt (fonte atual)."),
      new Paragraph("2. Verificação ad-hoc + build no Android Studio (Kotlin/JDK23 — blocker conhecido)."),
      new Paragraph("3. Validar no emulador contra dados históricos (procurar episódios de rebote pós-hipo com nadir 71-90 e medir impacto)."),
      new Paragraph("4. Migração ao celular apenas em lote B (comportamento), após validação — conforme decisão 05/Ago."),
    ],
  }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync("/tmp/emu_ns_24h/analise_2026-08-11_proposta_rebote_pos_hipo_arquivada.docx", buf);
  console.log("DOCX gerado: /tmp/emu_ns_24h/analise_2026-08-11_proposta_rebote_pos_hipo_arquivada.docx");
});
