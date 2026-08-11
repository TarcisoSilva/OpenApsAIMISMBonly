// gerar_proposta_v4.js — Documento v4: Eliminar ISF da profile no DetermineBasalAdapterAIMI
const { Document, Packer, Paragraph, HeadingLevel, Table, TableRow, TableCell, WidthType } = require("docx");
const fs = require("fs");

const doc = new Document({
  sections: [{
    properties: {},
    children: [
      new Paragraph({ text: "Proposta v4 — Eliminar ISF da profile no DetermineBasalAdapterAIMI", heading: HeadingLevel.TITLE }),
      new Paragraph({ text: "Documento de implementação — 09/08/2026 | Baseado nas decisões do usuário (Perguntas 1-5)", heading: HeadingLevel.HEADING_2 }),

      new Paragraph({ text: "1. OBJETIVOS DO USUÁRIO", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("1. Auto-ajuste atualiza o HF (ok, funcionando e implementado)."),
      new Paragraph("2. HF e ISF da profile têm o mesmo comportamento (validado: equalização manteve SMBs iguais porque se somam na L1216)."),
      new Paragraph("3. O ISF da profile DEIXA de ser usado no DetermineBasalAdapterAIMI.kt — usar o HF no lugar."),

      new Paragraph({ text: "2. DECISÕES DO USUÁRIO (respostas às perguntas)", heading: HeadingLevel.HEADING_1 }),
      new Table({
        width: { size: 100, type: WidthType.PERCENTAGE },
        rows: [
          new TableRow({ children: [new TableCell({ children: [new Paragraph({ text: "Pergunta", bold: true })] }), new TableCell({ children: [new Paragraph({ text: "Decisão", bold: true })] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("1. Fórmula (L1661) divisor 55")] }), new TableCell({ children: [new Paragraph("(a) Manter divisor 55 — fórmula intacta")] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("2. Delta Lock (L1690)")] }), new TableCell({ children: [new Paragraph("(b) HF × (tdd/6) — mais protetor (média +84%)")] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("3. IOB/Autosens/COB")] }), new TableCell({ children: [new Paragraph("Só o adapter — fora do escopo")] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("4. ISF da profile")] }), new TableCell({ children: [new Paragraph("Fica como está — sem mudanças")] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("5. Espelhar HF→ISF")] }), new TableCell({ children: [new Paragraph("Fase futura")] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("6. PK/PD (L315)")] }), new TableCell({ children: [new Paragraph("Mudar para HF também")] })] }),
        ],
      }),

      new Paragraph({ text: "3. VALIDAÇÃO COM DADOS REAIS (janela 08:23→14:01)", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• ISF profile vs HF: 22/24 horas idênticas (dif média 0.1 mg/dL) — equalização perfeita."),
      new Paragraph("• SMBs: emu 4.60U vs cel 5.60U (dif = timing/sync ~1h, padrão idêntico)."),
      new Paragraph("• Delta Lock com HF: vSens sobe +55-113% nas quedas → MAIS protetor (seguro)."),
      new Paragraph("• Descoberta: o tdd do Delta Lock é calculateTDDMetrics (~32), não tdd7DaysPerHour (2.08)."),

      new Paragraph({ text: "4. PROBLEMA DE ORDEM DESCOBERTO", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("A L315 (PK/PD) roda ANTES da leitura do hourlyfactor (L1491). Se trocarmos L315 para HF sem mover a leitura, leria valor antigo (0.0) — BUG."),
      new Paragraph("SOLUÇÃO: mover o bloco de leitura do hourlyfactor (L1491-1520) para o INÍCIO do setData (antes da L301)."),

      new Paragraph({ text: "5. AS 7 MUDANÇAS DE CÓDIGO", heading: HeadingLevel.HEADING_1 }),
      new Paragraph({ text: "MUDANÇA 0 — Mover leitura do hourlyfactor para início do setData:", bold: true }),
      new Paragraph("ANTES: L1491-1520 (dentro do setData, após uso na L315)"),
      new Paragraph("DEPOIS: bloco movido para logo após o início do setData (L~1350), antes da L301."),
      new Paragraph({ text: "MUDANÇA 1 — L237 (invoke fallback):", bold: true }),
      new Paragraph("ANTES: this.variableSensitivity = profileFunction.getProfile()?.getIsfMgdl()?.toDouble() ?: 45.0"),
      new Paragraph("DEPOIS: this.variableSensitivity = hourlyfactor"),
      new Paragraph({ text: "MUDANÇA 2 — L1425 (setData guard):", bold: true }),
      new Paragraph("ANTES: this.variableSensitivity = profile.getIsfMgdl().toDouble()"),
      new Paragraph("DEPOIS: this.variableSensitivity = hourlyfactor"),
      new Paragraph({ text: "MUDANÇA 3 — L1671 (setData fallback fórmula):", bold: true }),
      new Paragraph("ANTES: variableSensitivity = profileFunction.getProfile()?.getIsfMgdl() ?: 45.0"),
      new Paragraph("DEPOIS: variableSensitivity = hourlyfactor"),
      new Paragraph({ text: "MUDANÇA 4 — L1690 (Delta Lock):", bold: true }),
      new Paragraph("ANTES: if (delta <= -1.0 && bg < 160) variableSensitivity = (profileFunction.getProfile()?.getIsfMgdl() ?: 45.0) * (tdd.toFloat()/6)"),
      new Paragraph("DEPOIS: if (delta <= -1.0 && bg < 160) variableSensitivity = hourlyfactor * (tdd.toFloat()/6)"),
      new Paragraph({ text: "MUDANÇA 5 — L1701 (ProfileISF logs):", bold: true }),
      new Paragraph("ANTES: ProfileISF = profileFunction.getProfile()?.getIsfMgdl() ?: 45.0"),
      new Paragraph("DEPOIS: ProfileISF = hourlyfactor"),
      new Paragraph({ text: "MUDANÇA 6 — L315 (PK/PD):", bold: true }),
      new Paragraph("ANTES: val profileIsf: Double = profileFunction.getProfile()?.getIsfMgdl()?.toDouble() ?: 45.0"),
      new Paragraph("DEPOIS: val profileIsf: Double = hourlyfactor"),

      new Paragraph({ text: "6. RESULTADO ESPERADO", heading: HeadingLevel.HEADING_1 }),
      new Paragraph("• Zero referências a profile.getIsfMgdl() no DetermineBasalAdapterAIMI.kt."),
      new Paragraph("• variableSensitivity passa a derivar do HF (auto-ajustado) em TODAS as escritas."),
      new Paragraph("• Leitura L1216: soma HF + HF (via variável) — comportamento desejado."),
      new Paragraph("• IOB/Autosens/COB intactos (usam profile fora do adapter)."),

      new Paragraph({ text: "7. RISCOS E MITIGAÇÃO", heading: HeadingLevel.HEADING_1 }),
      new Table({
        width: { size: 100, type: WidthType.PERCENTAGE },
        rows: [
          new TableRow({ children: [new TableCell({ children: [new Paragraph({ text: "Risco", bold: true })] }), new TableCell({ children: [new Paragraph({ text: "Mitigação", bold: true })] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("Delta Lock +55-113% vSens")] }), new TableCell({ children: [new Paragraph("Mais protetor (SMB menor nas quedas) — seguro")] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("HF=0 (bug) → divisão por zero")] }), new TableCell({ children: [new Paragraph("Usar coerceAtLeast(1.0) nos fallbacks")] })] }),
          new TableRow({ children: [new TableCell({ children: [new Paragraph("Ordem L315 antes de L1491")] }), new TableCell({ children: [new Paragraph("MUDANÇA 0: mover leitura do HF para início")] })] }),
        ],
      }),
    ],
  }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync("/Volumes/M2/Users/tarciso/Documents/Mudanças AIMI/ANALISE/proposta_remover_isf_profile_2026-08-09.docx", buf);
  console.log("DOCX v4 criado");
});
