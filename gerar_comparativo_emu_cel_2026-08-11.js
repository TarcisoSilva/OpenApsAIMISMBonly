// gerar_comparativo_emu_cel_2026-08-11.js — Comparativo Emulador × Celular 24h
// Fluxo: node gerar_comparativo_emu_cel_2026-08-11.js && soffice --headless --convert-to doc
const { Document, Packer, Paragraph, HeadingLevel, Table, TableRow, TableCell, WidthType } = require("docx");
const fs = require("fs");

// Dados da tabela por hora (10/08 08:00 -> 11/08 08:00 BRT)
// [hora, BG, cel#, celU, celIOB, emu#, emuU, emuIOB, difU]
const rows = [
  ["10/08 08", 101, 2, "0.60", "2.08", 2, "0.35", "2.30", "+0.25"],
  ["09", 116, 9, "2.10", "2.79", 7, "1.70", "2.60", "+0.40"],
  ["10", 110, 0, "0.00", "2.33", 0, "0.00", "2.22", "0.00"],
  ["11", 97, 4, "2.30", "1.73", 4, "2.35", "1.50", "-0.05"],
  ["12", 98, 4, "1.00", "2.62", 4, "1.35", "2.58", "-0.35"],
  ["13", 111, 6, "1.80", "3.01", 8, "1.90", "3.11", "-0.10"],
  ["14", 115, 5, "1.50", "3.00", 2, "0.80", "2.99", "+0.70"],
  ["15", 115, 4, "1.20", "3.09", 3, "1.20", "2.70", "0.00"],
  ["16", 110, 3, "1.00", "2.63", 2, "1.15", "2.02", "-0.15"],
  ["17", 113, 7, "1.60", "2.95", 6, "1.15", "2.93", "+0.45"],
  ["18", 109, 5, "1.70", "2.54", 4, "1.90", "2.32", "-0.20"],
  ["19", 124, 7, "1.00", "2.98", 6, "0.70", "2.97", "+0.30"],
  ["20", 128, 6, "1.70", "2.74", 6, "1.65", "2.76", "+0.05"],
  ["21", 139, 4, "3.00", "3.16", 10, "2.25", "3.31", "+0.75"],
  ["22", 143, 0, "0.00", "3.61", 2, "0.15", "3.23", "-0.15"],
  ["23", 134, 4, "0.50", "2.31", 4, "0.35", "1.92", "+0.15"],
  ["11/08 00", 135, 8, "1.60", "2.40", 7, "1.45", "2.02", "+0.15"],
  ["01", 117, 1, "0.30", "1.92", 0, "0.00", "1.50", "+0.30"],
  ["02", 110, 5, "1.20", "1.73", 4, "1.30", "1.54", "-0.10"],
  ["03", 105, 3, "0.70", "1.60", 3, "0.60", "1.46", "+0.10"],
  ["04", 104, 5, "0.50", "1.56", 7, "0.45", "1.61", "+0.05"],
  ["05", 106, 4, "1.10", "1.43", 3, "0.70", "1.36", "+0.40"],
  ["06", 94, 2, "0.60", "1.81", 0, "0.00", "1.16", "+0.60"],
  ["07", 83, 2, "0.70", "1.30", 3, "1.25", "0.72", "-0.55"],
];

const header = ["Hora", "BG", "Cel #", "Cel U", "Cel IOB", "Emu #", "Emu U", "Emu IOB", "Dif U"];
const th = (t) => new TableCell({ children: [new Paragraph({ text: t, bold: true })] });
const td = (t) => new TableCell({ children: [new Paragraph(String(t))] });

const table = new Table({
  width: { size: 100, type: WidthType.PERCENTAGE },
  rows: [
    new TableRow({ children: header.map(th) }),
    ...rows.map(r => new TableRow({ children: r.map(td) })),
  ],
});

const doc = new Document({
  sections: [{
    properties: {},
    children: [
      new Paragraph({ text: "Comparativo Emulador × Celular — 24h", heading: HeadingLevel.TITLE }),
      new Paragraph({ text: "Janela: 10/08/2026 08:00 → 11/08/2026 08:00 BRT | Gerado em 11/08/2026", heading: HeadingLevel.HEADING_2 }),

      new Paragraph({ text: "1. RESUMO EXECUTIVO", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• O BG é controlado APENAS pelo celular (dosa na BG real). O emulador NÃO influencia a BG — o comparativo mede quão semelhante o emu foi ao celular em SMB e IOB."),
      new Paragraph("• Emu = 97% das doses (97 vs 100) e 89% da insulina (24,70U vs 27,70U) — levemente mais conservador, dentro da margem aceitável."),
      new Paragraph("• Correlações hora-a-hora: U = 0,902 | IOB médio = 0,945 — espelho de altíssima fidelidade."),
      new Paragraph("• Janela limpa: nenhum OpenAPS Offline / EMULATED_PUMP_SUSPEND dentro das 24h (todos em 07-08/08). Troca de sensor 07/08 15:41 e troca de insulina 08/08 08:13, ambos fora da janela. As diferenças NÃO são explicadas por bomba OFF."),
      new Paragraph("• Veredito: CELULAR teve o controle ligeiramente melhor — foi mais assertivo exatamente nas subidas reais (BG 101→143 à tarde). O emu ficou muito próximo e com perfil seguro (pico de IOB menor)."),

      new Paragraph({ text: "2. TOTAIS DA JANELA", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• Celular: 100 doses de SMB / 27,70 U"),
      new Paragraph("• Emulador: 97 doses de SMB / 24,70 U"),
      new Paragraph("• Emu/Cel: doses = 97% | U = 89%"),

      new Paragraph({ text: "3. TABELA POR HORA (BG = contexto real, não mérito do emu)", heading: HeadingLevel.HEADING_1 }),
      table,

      new Paragraph({ text: "4. ONDE HOUVE DIFERENÇAS (|dif| ≥ 0,4 U — 7 horas)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph({ text: "21h — a maior divergência (+0,75U) — FRAGMENTAÇÃO:", bold: true }),
      new Paragraph("• Celular: 4 doses de ~0,75U — assertivo no pico da subida (BG 139→143)."),
      new Paragraph("• Emulador: 10 doses de ~0,23U — pulverizou o SMB: dobrou o nº de ciclos com dose, com doses pequenas."),
      new Paragraph("• IOB final parecido (cel máx 4,76 vs emu 4,12), mas o emu entregou 25% menos insulina na maior subida do dia — menos eficaz para segurar subida forte."),
      new Paragraph({ text: "14h (+0,70U):", bold: true }),
      new Paragraph("• Emu deu 2 doses (0,80U) vs 5 do celular (1,50U). Mais contido, BG estável ~115."),
      new Paragraph({ text: "06h (+0,60U):", bold: true }),
      new Paragraph("• BG caindo (94): celular deu 2 doses pequenas, emu NÃO doseou. Conservador."),
      new Paragraph({ text: "17h (+0,45U), 09h (+0,40U), 05h (+0,40U):", bold: true }),
      new Paragraph("• Emu ~25-35% abaixo em subidas leves/moderadas."),
      new Paragraph({ text: "07h (−0,55U — única hora em que emu deu MAIS):", bold: true }),
      new Paragraph("• Emu 1,25U vs 0,70U do celular com BG real em 83 (nadir matinal). O emu via BG ~85-90 e tinha IOB baixo (0,72), então compensou — mas dosar mais que o celular no nadir é o único ponto de atenção real para segurança futura."),

      new Paragraph({ text: "5. ANÁLISE DO IOB", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• Espelho quase perfeito (corr 0,945): pico à tarde (3,0-3,6), queda gradual na madrugada (1,4-1,9), nadir às 07h."),
      new Paragraph("• Emu com pico máximo menor (4,12 vs 4,76 às 21h) — mais contido no cenário de maior risco."),
      new Paragraph("• Madrugada 01-03h: emu ~0,2-0,4 U abaixo do celular — IOB mais baixo dormindo = perfil seguro."),

      new Paragraph({ text: "6. CONCLUSÃO", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("1. Acerto na quantidade/doses: o emu acertou a cadência (97 vs 100 doses) e o timing (corr 0,90). Volume −11%: leve conservadorismo aceitável."),
      new Paragraph("2. Melhor controle: CELULAR, por pouco — mais assertivo nas subidas reais; o emu ficou devendo principalmente nas 21h (fragmentação) e 14h."),
      new Paragraph("3. Ajustes para o emu virar espelho fiel (estudo futuro):"),
      new Paragraph("   • 21h — fragmentação de SMB (10 doses pequenas vs 4 médias): perde eficácia em subidas fortes; diferença de comportamento do algoritmo, não de configuração."),
      new Paragraph("   • 07h — dose no nadir (BG 83): emu foi o único mais agressivo do dia; em produção, precisa da trava de BG baixa que o celular já tem."),
    ],
  }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync("/tmp/emu_ns_24h/analise_2026-08-11_emu_vs_celular_24h.docx", buf);
  console.log("DOCX gerado: /tmp/emu_ns_24h/analise_2026-08-11_emu_vs_celular_24h.docx");
});
