#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gera coletânea Word + PPT inspirada em @miimoska_ para campanha em Lages
"""
from docx import Document
from docx.shared import Inches, Pt, RGBColor, Emu
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.dml.color import ColorFormat
import os

OUT_DIR = "/Volumes/M2/Users/tarciso/Downloads/OpenApsAIMI-master_Plugin_OpenApsAIMI_Steps-DEV-2"
WORD_PATH = os.path.join(OUT_DIR, "Coletanea_Lembrancinhas_Campanha_Lages_Miimoska.docx")
PPT_PATH = os.path.join(OUT_DIR, "Apresentacao_Coletanea_Lages_Miimoska.pptx")

# Paleta
VERDE_SERRA = RGBColor(0x2A, 0x5E, 0x4F)  # #2A5E4F
VERDE_CLARO = RGBColor(0x3A, 0x8A, 0x6E)
PESSEGO = RGBColor(0xE8, 0x7D, 0x6B)  # peach accent
BEGE = "F5E6D3"
BEGE_RGB = RGBColor(0xF5, 0xE6, 0xD3)
CINZA_TEXTO = RGBColor(0x4A, 0x4A, 0x4A)
CINZA_CLARO = RGBColor(0x8A, 0x8A, 0x8A)

def set_cell_shading(cell, color_hex):
    tblCell = cell._tc
    tblCellProperties = tblCell.get_or_add_tcPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), color_hex)
    tblCellProperties.append(shd)

def set_cell_border(cell, **kwargs):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    tcBorders = tcPr.first_child_found_in("w:tcBorders")
    if tcBorders is None:
        tcBorders = OxmlElement('w:tcBorders')
        tcPr.append(tcBorders)
    for edge in ("top","left","bottom","right","insideH","insideV"):
        edge_data = kwargs.get(edge)
        if edge_data:
            tag = 'w:{}'.format(edge)
            element = tcBorders.find(qn(tag))
            if element is None:
                element = OxmlElement(tag)
                tcBorders.append(element)
            for key in ["val", "sz", "space", "color"]:
                if key in edge_data:
                    element.set(qn('w:{}'.format(key)), str(edge_data[key]))

def add_horizontal_line(paragraph, color="2A5E4F", width_pt=1):
    p = paragraph._p
    pPr = p.get_or_add_pPr()
    pBdr = OxmlElement('w:pBdr')
    pPr.append(pBdr)
    bottom = OxmlElement('w:bottom')
    bottom.set(qn('w:val'), 'single')
    bottom.set(qn('w:sz'), str(width_pt*4))
    bottom.set(qn('w:space'), '1')
    bottom.set(qn('w:color'), color)
    pBdr.append(bottom)

def style_heading(doc, text, level, color=VERDE_SERRA, size=14, alignment=WD_ALIGN_PARAGRAPH.LEFT):
    h = doc.add_heading(level=level)
    h.alignment = alignment
    run = h.add_run(text)
    run.font.color.rgb = color
    run.font.size = Pt(size)
    run.font.name = 'Calibri'
    run.bold = True
    if level == 1:
        run.font.size = Pt(20)
    elif level == 2:
        run.font.size = Pt(15)
    return h

# DADOS COLETÂNEA - 18 ideias baseadas no estilo miimoska_
categorias = [
    {
        "nome": "1. Cartões & Marcadores — Leves, baratos e ideais para deixar com moradores",
        "cor": "2A5E4F",
        "ideias": [
            {
                "id": "01",
                "nome": "Cartão Semente \"Semeando Esperança\"",
                "descricao": "Cartão 8,5×6,5cm (9 por folha) com frente aquarelada em tons de serra e verso com Isaías 55:11. Inclui envelope kraft mini. O morador planta o cartão — papel semente artesanal.",
                "materiais": "Papel offset 180g ou papel semente 150g, impressão laser colorida, envelope kraft 7×10cm",
                "custo": "R$ 0,18 a R$ 0,45/unidade",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "30 unid./hora",
                "dica_montagem": "Imprima 9 por folha A4, corte com guilhotina. Dobre envelope com gabarito (arquivo estilo miimoska_ 'envelope coração').",
                "texto_sugestivo": "\"A palavra de Deus não voltará sem resultados\" — Isa. 55:11",
                "inspiracao": "miimoska_ | Kit Cartões Campanha + Envelope Coração",
            },
            {
                "id": "02",
                "nome": "Marcador Magnético \"Lages, Cidade Acolhedora\"",
                "descricao": "Marcador 5×15cm dobrado com imã 10×2mm interno. Arte com araucária + pinheiro e frase \"Que Jeová abençoe sua leitura da Bíblia\".",
                "materiais": "Papel fotográfico 230g, manta magnética adesiva cortada, laminado fosco (opcional)",
                "custo": "R$ 0,35/unidade",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "40 unid./hora",
                "dica_montagem": "Imprima frente e verso, cole imã entre as duas partes, vinque ao meio. Arquivo 8 por folha.",
                "texto_sugestivo": "Salmo 1:2 — \"Seu prazer está na lei de Jeová\"",
                "inspiracao": "miimoska_ | Marcadores Magnéticos Teocráticos",
            },
            {
                "id": "03",
                "nome": "Card com Bala + QR Code \"Convite Digital\"",
                "descricao": "Cartão 7×10cm com bala de goma/fruit-tella fixada com fita dupla face e QR code para jw.org vídeo da campanha. Perfeito para comércio do centro de Lages.",
                "materiais": "Papel Couchê 250g, balinhas, fita, impressão do QR",
                "custo": "R$ 0,40/unidade",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "50 unid./hora",
                "dica_montagem": "Use perfurador circular 2,5cm para destacar bala. QR gerado em qrcode-monkey.com com logo.",
                "texto_sugestivo": "\"Venha ouvir as boas novas!\" - com horário e local do Salão",
                "inspiracao": "miimoska_ | Cartão Bala / Card Bombom",
            },
            {
                "id": "04",
                "nome": "Envelope \"Carta de Esperança\" + Folheto",
                "descricao": "Envelope 10×7cm estilo 'air mail' em kraft com carimbo \"Para você com carinho\" + mini folheto A6 dobrado (4 páginas).",
                "materiais": "Kraft 120g, impressão P&B interna, carimbo personalizado",
                "custo": "R$ 0,55/unidade",
                "dificuldade": "★★☆ Médio",
                "tempo": "25 unid./hora",
                "dica_montagem": "Molde envelope já vem com margem de cola; use régua de vinco. Folheto em sulfite 90g dobrado.",
                "texto_sugestivo": "Apocalipse 21:4 — promessa para tempos difíceis do frio/inverno",
                "inspiracao": "miimoska_ | Kit Cartas + Envelopes Teocráticos",
            },
        ]
    },
    {
        "nome": "2. Caixinhas & Embalagens — Lembrancinha que fica na mesa do morador",
        "cor": "E87D6B",
        "ideias": [
            {
                "id": "05",
                "nome": "Caixinha Milk \"Pão Diário\" (com pão de mel)",
                "descricao": "Caixinha milk 7×7×10cm com visor acetato e tag \"Que nunca nos falte o pão da vida — João 6:35\". Ideal para deixar pão de mel ou mini panetone (clima de Lages).",
                "materiais": "Papel Couchê 230g, acetato 10×5cm, fita de cetim, pão de mel 30g",
                "custo": "R$ 1,20 (caixa) + R$ 1,80 (recheio)",
                "dificuldade": "★★☆ Médio",
                "tempo": "20 unid./hora",
                "dica_montagem": "Vinque todas as dobras antes de colar. Use cola em bastão para agilidade. Arquivo miimoska_ já vem com marca de vinco.",
                "texto_sugestivo": "João 6:35",
                "inspiracao": "miimoska_ | Caixa Milk + Tag Vertical",
            },
            {
                "id": "06",
                "nome": "Caixinha Sushi + Brownie \"Doce Esperança\"",
                "descricao": "Caixinha sushi kraft 10×6×5cm com berço para brownie 40g + cartão 5cm. Tampa com janela em coração.",
                "materiais": "Kraft 250g, brownie, papel manteiga, tag",
                "custo": "R$ 1,00 + R$ 2,00 (brownie)",
                "dificuldade": "★★☆ Médio",
                "tempo": "18 unid./hora",
                "dica_montagem": "Forre com papel manteiga para não engordurar. Janela coração feita com cortador.",
                "texto_sugestivo": "Salmo 34:8 — \"Provem e vejam que Jeová é bom\"",
                "inspiracao": "miimoska_ | Caixinha Sushi / Caixa para brownie",
            },
            {
                "id": "07",
                "nome": "Bolsinha Casinha \"Lar com Jeová\"",
                "descricao": "Bolsinha em formato de casinha 9×9×4cm com telhadinho, alusiva ao texto \"Que seu lar seja abençoado\". Cabe 3 bis ou chá.",
                "materiais": "Papel fotográfico 180g, fita organza, 2-3 sachês de chá (mate/chá serrano — a cara de Lages)",
                "custo": "R$ 0,90 (embalagem) + R$ 0,60 (chás)",
                "dificuldade": "★★☆ Médio",
                "tempo": "22 unid./hora",
                "dica_montagem": "Cole telhado por último. Use chá de maçã/canela — combina com inverno lageano.",
                "texto_sugestivo": "Josué 24:15 — \"Eu e minha família serviremos a Jeová\"",
                "inspiracao": "miimoska_ | Sacolinha Casinha / Bolsinha",
            },
            {
                "id": "08",
                "nome": "Envelope Travesseiro \"Abraço Quentinho\"",
                "descricao": "Travesseirinho 10×12cm com fechamento em fita cetim + escalda-pés artesanal (sal grosso + ervas) ou 2 balas toffee. Tema inverno de Lages.",
                "materiais": "Papel 180g perolizado, fita cetim 1,5cm, sal aromático em saquinho zip 5×7cm",
                "custo": "R$ 0,75 + R$ 0,50 (sal)",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "35 unid./hora",
                "dica_montagem": "Dobra simples, sem cola — só vinco e fita. Faça sal com 100g sal grosso + lavanda.",
                "texto_sugestivo": "Isaías 41:10 — \"Não tenha medo, pois estou com você\"",
                "inspiracao": "miimoska_ | Travesseirinho / Almofadinha",
            },
            {
                "id": "09",
                "nome": "Caixa Cubo 6×6cm \"Tesouro\"",
                "descricao": "Cubo com tampa acetato, laço chanel e pérolas adesivas. Dentro: 1 ferrero ou 4 balas finas + versículo em mini pergaminho enrolado.",
                "materiais": "Papel 250g, acetato, pérola, ferrero, pergaminho sulfite 60g",
                "custo": "R$ 1,10 + R$ 1,50 (bombom)",
                "dificuldade": "★★☆ Médio",
                "tempo": "20 unid./hora",
                "dica_montagem": "Cole tampa acetato com fita dupla face transparente para acabamento limpo.",
                "texto_sugestivo": "Mateus 6:21 — \"Onde estiver seu tesouro...\"",
                "inspiracao": "miimoska_ | Cubo com visor / Caixa 6×6",
            },
        ]
    },
    {
        "nome": "3. Doces & Mimos quentinhos — a cara do inverno lageano",
        "cor": "3A8A6E",
        "ideias": [
            {
                "id": "10",
                "nome": "Solapa para Saquinho de Chá + Chocolate",
                "descricao": "Solapa 9×12cm dobrada sobre saquinho zip com 2 chás + 1 chocolate 9g. Arte com araucárias e flocos de neve.",
                "materiais": "Papel 180g, saquinho zip 7×10cm, chás, chocolate, grampeador",
                "custo": "R$ 0,30 + R$ 0,90 (recheio)",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "45 unid./hora",
                "dica_montagem": "Grampeie solapa no saquinho. Produza em série: corte 6 solapas por A4.",
                "texto_sugestivo": "\"Que suas palavras sejam como chá quentinho no frio\" — Provérbios 16:24 adaptado",
                "inspiracao": "miimoska_ | Solapa para Saco Transparente",
            },
            {
                "id": "11",
                "nome": "Rótulo para Pirulito \"Pirulito da Alegria\"",
                "descricao": "Rótulo circular 7cm com abas para pirulito pop, tema balão + nuvem. Verso com Rom. 12:12.",
                "materiais": "Papel fotográfico 180g, pirulito Big Big/pop, fita dupla face",
                "custo": "R$ 0,15 + R$ 0,40 (pirulito)",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "60 unid./hora",
                "dica_montagem": "Fure centro com estilete para haste. Corte circular com perforador 7cm.",
                "texto_sugestivo": "Romanos 12:12 — \"Alegrem-se na esperança\"",
                "inspiracao": "miimoska_ | Rótulo Pirulito",
            },
            {
                "id": "12",
                "nome": "Porta Bis Duplo \"Forte Abraço\"",
                "descricao": "Luva para 2 bis (tradicional) com tag \"Um abraço quentinho para aquecer seu dia\". Arte em tons terrosos.",
                "materiais": "Papel 180g, 2 bis, cola bastão",
                "custo": "R$ 0,25 + R$ 1,00 (bis)",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "50 unid./hora",
                "dica_montagem": "Dobra simples em U. Use régua para vinco perfeito. 6 por folha.",
                "texto_sugestivo": "1 Tessalonicenses 5:11 — \"Continuem a encorajar uns aos outros\"",
                "inspiracao": "miimoska_ | Porta Bis",
            },
            {
                "id": "13",
                "nome": "Tag para Garrafinha de Água 200ml \"Água da Vida\"",
                "descricao": "Faixa 20×4cm para envolver garrafinha + copo térmico descartável (feira de Lages). Mensagem sobre \"água que dá vida\".",
                "materiais": "Papel Couchê 180g, garrafa 200ml, fita dupla face",
                "custo": "R$ 0,20 + R$ 0,65 (água)",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "55 unid./hora",
                "dica_montagem": "Imprima 5 tiras por A4. Envolva e fixe com pingo de cola quente.",
                "texto_sugestivo": "João 4:14 e Apoc. 22:17 — \"Venha quem tiver sede\"",
                "inspiracao": "miimoska_ | Faixa / Cinta para garrafa e latinha",
            },
        ]
    },
    {
        "nome": "4. Papelaria criativa & Utilitários — lembrança que dura",
        "cor": "9B6B4A",
        "ideias": [
            {
                "id": "14",
                "nome": "Bloquinho 7×10cm \"Anotações da Leitura\"",
                "descricao": "Bloquinho com capa 300g (arte araucária) + 20 folhas sulfite 75g + mini lápis. Para moradores anotarem textos bíblicos.",
                "materiais": "Capa couchê 300g, miolo sulfite, grampo saddle, mini lápis, elástico",
                "custo": "R$ 0,85/unidade (sem lápis) / R$ 1,60 (com lápis)",
                "dificuldade": "★★★ Avançado (encadernação)",
                "tempo": "15 unid./hora",
                "dica_montagem": "Imprima capa em A4 (4 por folha), miolo em A4 dobrado e grampeado. Furador para elástico.",
                "texto_sugestivo": "Salmo 119:105 — \"Tua palavra é lâmpada\"",
                "inspiracao": "miimoska_ | Bloquinhos / Card com lápis",
            },
            {
                "id": "15",
                "nome": "Sachê Perfumado \"Bom Perfume\"",
                "descricao": "Envelope 9×12cm com sachê de sagu aromatizado ou sabonete mini 15g + tag \"Seja o bom perfume de Cristo\". Cheirinho que fica na gaveta.",
                "materiais": "Papel 180g perolizado, sachê TNT 7×9cm, essência lavanda, sabonete",
                "custo": "R$ 0,70 + R$ 0,90 (sabonete)",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "30 unid./hora",
                "dica_montagem": "Costure sachê TNT com máquina ou cola quente. Pingue 5 gotas de essência.",
                "texto_sugestivo": "2 Coríntios 2:15",
                "inspiracao": "miimoska_ | Envelope com sachê / Sabonete",
            },
            {
                "id": "16",
                "nome": "Imã de Geladeira Calendário + Oração",
                "descricao": "Imã 7×10cm com calendário de 2 meses + oração do Pai Nosso / texto campanha + espaço para anotar estudo.",
                "materiais": "Papel fotográfico 230g + manta magnética, laminação fosca",
                "custo": "R$ 0,55/unidade",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "30 unid./hora (com guilhotina)",
                "dica_montagem": "Plastifique para durar 2 meses na geladeira. Imã em rolo cortado 7×10.",
                "texto_sugestivo": "Mateus 6:9-13",
                "inspiracao": "miimoska_ | Imã + Calendário",
            },
            {
                "id": "17",
                "nome": "Marcador de Página Régua \"Leia a Bíblia\"",
                "descricao": "Régua marcador 4×18cm em papel 300g com escala cm + livros da Bíblia impressos no verso. Útil para crianças e estudantes.",
                "materiais": "Couchê 300g laminado, corte com cantos arredondados",
                "custo": "R$ 0,28/unidade",
                "dificuldade": "★☆☆ Fácil",
                "tempo": "40 unid./hora",
                "dica_montagem": "Cantoneira para arredondar cantos. Laminação obrigatória para durabilidade.",
                "texto_sugestivo": "2 Timóteo 3:16 — \"Toda Escritura é inspirada\"",
                "inspiracao": "miimoska_ | Régua Marcador",
            },
            {
                "id": "18",
                "nome": "Kit \"Estação da Pregação\" — 3 mimos em 1",
                "descricao": "Caixa 12×9×4cm comportando: 1 cartão semente + 1 chá + 1 marcador magnético. Para visitas especiais/estudos. Fecha com cinta.",
                "materiais": "Caixa 250g, cinta 30×4cm, recheios acima",
                "custo": "R$ 1,20 + R$ 1,80 (recheios)",
                "dificuldade": "★★☆ Médio",
                "tempo": "15 kits/hora",
                "dica_montagem": "Monte caixa base e só depois insira recheio. Cinta com frase da campanha (editável).",
                "texto_sugestivo": "Personalizável com tema da campanha vigente",
                "inspiracao": "miimoska_ | Kits com múltiplos itens (best-seller)",
            },
        ]
    }
]

# ---------- Gerar DOCX ----------

document = Document()

# Configurar margens
sections = document.sections
for section in sections:
    section.top_margin = Inches(0.5)
    section.bottom_margin = Inches(0.5)
    section.left_margin = Inches(0.6)
    section.right_margin = Inches(0.6)
    section.header_distance = Inches(0.3)
    section.footer_distance = Inches(0.3)

# Estilos base
style = document.styles['Normal']
style.font.name = 'Calibri'
style.font.size = Pt(9.5)
style.font.color.rgb = CINZA_TEXTO
style.paragraph_format.space_after = Pt(4)
style.paragraph_format.line_spacing = 1.05

# Helper para adicionar parágrafo estilizado
def add_para(text, bold=False, italic=False, color=None, size=None, align=None, space_after=4, space_before=0, font_name=None):
    p = document.add_paragraph()
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.space_before = Pt(space_before)
    if align:
        p.alignment = align
    run = p.add_run(text)
    run.bold = bold
    run.italic = italic
    if color:
        run.font.color.rgb = color
    if size:
        run.font.size = Pt(size)
    if font_name:
        run.font.name = font_name
    return p

def add_mixed_para(parts, align=None, space_after=4, space_before=0):
    # parts = list of dict {text, bold, italic, color, size}
    p = document.add_paragraph()
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.space_before = Pt(space_before)
    if align:
        p.alignment = align
    for part in parts:
        run = p.add_run(part.get("text",""))
        run.bold = part.get("bold", False)
        run.italic = part.get("italic", False)
        if part.get("color"):
            run.font.color.rgb = part["color"]
        if part.get("size"):
            run.font.size = Pt(part["size"])
        if part.get("font"):
            run.font.name = part["font"]
    return p

# --- CAPA ---
# Faixa superior verde
p = document.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(0)
run = p.add_run("  ")
# vamos criar tabela para capa
table = document.add_table(rows=1, cols=1)
table.alignment = WD_TABLE_ALIGNMENT.CENTER
cell = table.cell(0,0)
set_cell_shading(cell, "2A5E4F")
cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
cell_paras = cell.paragraphs
cp = cell_paras[0]
cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = cp.add_run("  ✦  COLETÂNEA TEOCRÁTICA  ✦  ")
r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
r.font.size = Pt(8)
r.font.name = 'Calibri'
r.bold = True
r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
cp2 = cell.add_paragraph()
cp2.alignment = WD_ALIGN_PARAGRAPH.CENTER
r2 = cp2.add_run("INSPIRADA NO ACERVO @MIIMOSKA_  •  KARINE FRANÇA | ARQUIVOS TEOCRÁTICOS")
r2.font.color.rgb = RGBColor(0xD9,0xE8,0xE0)
r2.font.size = Pt(6.5)
r2.font.name = 'Calibri'

document.add_paragraph().paragraph_format.space_after = Pt(6)

# Título principal
t1 = document.add_paragraph()
t1.alignment = WD_ALIGN_PARAGRAPH.CENTER
t1.paragraph_format.space_after = Pt(2)
r = t1.add_run("Coletânea de Ideias")
r.font.color.rgb = VERDE_SERRA
r.font.size = Pt(28)
r.bold = True
r.font.name = 'Calibri Light'
# linha
t2 = document.add_paragraph()
t2.alignment = WD_ALIGN_PARAGRAPH.CENTER
t2.paragraph_format.space_after = Pt(2)
r = t2.add_run("DE LEMBRANCINHAS")
r.font.color.rgb = PESSEGO
r.font.size = Pt(28)
r.bold = True
r.font.name = 'Calibri'
# subtitulo
t3 = document.add_paragraph()
t3.alignment = WD_ALIGN_PARAGRAPH.CENTER
t3.paragraph_format.space_after = Pt(8)
r = t3.add_run("para Campanha de Pregação em LAGES • Serra Catarinense")
r.font.color.rgb = CINZA_TEXTO
r.font.size = Pt(11)
r.bold = False
r.italic = True
r.font.name = 'Calibri'

# Linha decorativa
p = document.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(8)
r = p.add_run("─ ─ ─  ✂  ─ ─ ─")
r.font.color.rgb = RGBColor(0xC8,0xC8,0xC8)
r.font.size = Pt(9)

# Box bege descrição
tbl = document.add_table(rows=1, cols=1)
tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
c = tbl.cell(0,0)
set_cell_shading(c, "FDF6EE")
c.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
# borda
from docx.oxml.ns import qn
tblPr = tbl._tbl.tblPr
tblBorders = OxmlElement('w:tblBorders')
for border_name in ['top','left','bottom','right','insideH','insideV']:
    border = OxmlElement(f'w:{border_name}')
    border.set(qn('w:val'), 'single')
    border.set(qn('w:sz'), '4')
    border.set(qn('w:space'), '0')
    border.set(qn('w:color'), 'E8D9C0')
    tblBorders.append(border)
tblPr.append(tblBorders)
p = c.paragraphs[0]
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("18 ideias práticas •  Imprima, monte e encante  •  Produção física liberada\n")
r.font.color.rgb = VERDE_SERRA
r.font.size = Pt(9)
r.bold = True
r.font.name = 'Calibri'
p2 = c.add_paragraph()
p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p2.add_run("Baseado no estilo 'imprima, monte ✂️ e venda' da @miimoska_ (277 posts • 6,6k seguidores)\nNordestina, sim senhor 🌵 • Papelaria teocrática criativa para Testemunhas de Jeová")
r.font.color.rgb = CINZA_TEXTO
r.font.size = Pt(7.5)
r.font.name = 'Calibri'
r.italic = True

document.add_paragraph().paragraph_format.space_after = Pt(10)

# Infos Lages
tbl2 = document.add_table(rows=1, cols=3)
tbl2.alignment = WD_TABLE_ALIGNMENT.CENTER
tbl2.autofit = True
# Col 1
c1 = tbl2.cell(0,0)
c1.width = Inches(2.2)
set_cell_shading(c1, "2A5E4F")
p = c1.paragraphs[0]
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("❄️  LAGES • SC")
r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
r.font.size = Pt(8)
r.bold = True
p2 = c1.add_paragraph()
p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p2.add_run("Inverno acolhedor\nChás, araucárias\n& hospitalidade serrana")
r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
r.font.size = Pt(7)
# Col 2
c2 = tbl2.cell(0,1)
set_cell_shading(c2, "E87D6B")
p = c2.paragraphs[0]
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("⏱  TEMPO MÉDIO")
r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
r.font.size = Pt(8)
r.bold = True
p2 = c2.add_paragraph()
p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p2.add_run("15 a 60 unid./hora\nIdeal para mutirão\npós-reunião")
r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
r.font.size = Pt(7)
# Col 3
c3 = tbl2.cell(0,2)
set_cell_shading(c3, "F5E6D3")
p = c3.paragraphs[0]
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("💰  CUSTO MÉDIO")
r.font.color.rgb = VERDE_SERRA
r.font.size = Pt(8)
r.bold = True
p2 = c3.add_paragraph()
p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p2.add_run("R$ 0,15 a R$ 3,00/unid.\nCabe em qualquer\norçamento")
r.font.color.rgb = CINZA_TEXTO
r.font.size = Pt(7)

# Rodapé capa
document.add_paragraph().paragraph_format.space_after = Pt(14)
p = document.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(2)
r = p.add_run("Preparado com carinho para a congregação •  Agosto 2026  •  Material de apoio — não para revenda digital")
r.font.color.rgb = CINZA_CLARO
r.font.size = Pt(6.5)
r.italic = True
p2 = document.add_paragraph()
p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p2.add_run("Referências: instagram.com/miimoska_  •  wa.me/c/557183611110  •  t.me/miimoska  •  Grupo WhatsApp Miimoska")
r.font.color.rgb = CINZA_CLARO
r.font.size = Pt(6)
r.font.name = 'Calibri'

# Quebra de página? Adicionar mas manter fluxo
document.add_page_break()

# --- SUMÁRIO ---
style_heading(document, "Sumário", 1, VERDE_SERRA, 16)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 2)
sum_items = [
    ("01", "Como usar esta coletânea + Nota sobre @miimoska_", "3"),
    ("02", "Visão geral das 4 categorias", "3"),
    ("03", "Categoria 1 — Cartões & Marcadores (ideias 01 a 04)", "4"),
    ("04", "Categoria 2 — Caixinhas & Embalagens (05 a 09)", "6"),
    ("05", "Categoria 3 — Doces & Mimos quentinhos (10 a 13)", "8"),
    ("06", "Categoria 4 — Papelaria & Utilitários (14 a 18)", "9"),
    ("07", "Tabela comparativa de custo × tempo × impacto", "11"),
    ("08", "Plano de ação para Lages (clima, logística e estoque)", "11"),
    ("09", "Guia de impressão e montagem (papéis, cortes, dicas miimoska_)", "12"),
    ("10", "Checklist de compras & fornecedores", "13"),
    ("11", "Textos bíblicos sugeridos para a campanha", "13"),
    ("12", "Próximos passos e agradecimentos", "14"),
]
for num, title, pg in sum_items:
    p = document.add_paragraph()
    p.paragraph_format.space_after = Pt(2)
    pPr = p._p.get_or_add_pPr()
    tabs = OxmlElement('w:tabs')
    tab = OxmlElement('w:tab')
    tab.set(qn('w:val'), 'right')
    tab.set(qn('w:leader'), 'dot')
    tab.set(qn('w:pos'), '9200')
    tabs.append(tab)
    pPr.append(tabs)
    r = p.add_run(f"{num}   {title}\t{pg}")
    r.font.size = Pt(8.5)
    r.font.name = 'Calibri'
    if num in ["01","02"]:
        r.bold = True
        r.font.color.rgb = VERDE_SERRA
    else:
        r.font.color.rgb = CINZA_TEXTO

# Nota sobre uso
p = document.add_paragraph()
p.paragraph_format.space_before = Pt(8)
pf = p.paragraph_format
pf.left_indent = Inches(0.15)
pf.right_indent = Inches(0.15)
# shading
pPr = p._p.get_or_add_pPr()
shd = OxmlElement('w:shd')
shd.set(qn('w:val'), 'clear')
shd.set(qn('w:color'), 'auto')
shd.set(qn('w:fill'), 'FDF6EE')
pPr.append(shd)
r = p.add_run("💡  Como ler os cards:  cada ideia traz custo por unidade, nível de dificuldade, tempo de produção e referência ao estilo miimoska_. Todas são arquivos digitais 'imprima e monte' — você compra o PDF uma vez e imprime quantas vezes quiser. Produção física liberada pela artista.")
r.font.size = Pt(7)
r.italic = True
r.font.color.rgb = RGBColor(0x7A,0x6A,0x4F)

# --- COMO USAR ---
style_heading(document, "Como usar esta coletânea", 1, VERDE_SERRA, 15)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 1)
add_para("Esta coletânea foi criada a partir da análise pública do perfil @miimoska_ (Karine França — 'arquivos teocráticos | Imprima, monte ✂️ e venda'). O Instagram exige login para ver todos os vídeos e fotos, portanto a curadoria foi feita via descrições públicas, catálogo no WhatsApp (wa.me/c/557183611110), canal no Telegram (t.me/miimoska) e padrão visual das 277 publicações — todas focadas em papelaria teocrática DIY para Testemunhas de Jeová, com produção física liberada.", size=8, color=CINZA_TEXTO, space_after=6)

# Box aviso
tbl = document.add_table(rows=1, cols=1)
c = tbl.cell(0,0)
set_cell_shading(c, "FFF3F3")
# border redish
tblPr = tbl._tbl.tblPr
tblBorders = OxmlElement('w:tblBorders')
for bn in ['top','left','bottom','right']:
    b = OxmlElement(f'w:{bn}')
    b.set(qn('w:val'), 'single'); b.set(qn('w:sz'), '4'); b.set(qn('w:space'), '0'); b.set(qn('w:color'), 'E8AFAF')
    tblBorders.append(b)
tblPr.append(tblBorders)
p = c.paragraphs[0]
r = p.add_run("⚠️  Nota de transparência & direitos autorais: ")
r.bold = True; r.font.size = Pt(7.5); r.font.color.rgb = RGBColor(0x8B,0x3A,0x3A)
r2 = p.add_run("as artes originais da @miimoska_ são pagas (via WhatsApp/Catálogo) e protegidas por direitos autorais. Esta coletânea NÃO copia arquivos dela, mas se inspira em seus formatos (caixa milk, porta bis, solapa, marcador magnético etc.) para que você saiba exatamente o que pedir no catálogo dela. Sempre compre o arquivo oficial antes de imprimir.")
r2.font.size = Pt(7.5); r2.font.color.rgb = RGBColor(0x6B,0x4A,0x4A)

add_para("Como escolher:", bold=True, color=VERDE_SERRA, size=9, space_before=6, space_after=2)
bullets = [
    "Campanha curta / muitos moradores? Priorize Cartões & Marcadores (ideias 01–04) — R$ 0,15 a R$ 0,55, produção relâmpago.",
    "Quer deixar lembrança na casa? Vá de Caixinhas (05–09) — ficam na mesa e geram conversa.",
    "Frio de Lages (5–15 °C no inverno): aposte nos 'mimos quentinhos' (08, 10, 13) com chá, chocolate e escalda-pés.",
    "Estudos e revisitas? Invista em utilitários (14–18) — bloquinho, imã e régua duram meses.",
]
for b in bullets:
    p = document.add_paragraph(style='List Bullet')
    p.paragraph_format.left_indent = Inches(0.3)
    p.paragraph_format.space_after = Pt(2)
    run = p.add_run(b)
    run.font.size = Pt(8)
    run.font.color.rgb = CINZA_TEXTO

# --- VISÃO GERAL ---
style_heading(document, "Visão geral das 4 categorias", 1, VERDE_SERRA, 15)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 1)
# Tabela visão geral
tbl = document.add_table(rows=1, cols=4)
tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
tbl.autofit = False
tbl.columns[0].width = Inches(1.8)
tbl.columns[1].width = Inches(1.8)
tbl.columns[2].width = Inches(1.8)
tbl.columns[3].width = Inches(1.8)
headers = ["📮  Cartões & Marcadores\n4 ideias • 01–04", "📦  Caixinhas & Embalagens\n5 ideias • 05–09", "🍫  Doces & Quentinhos\n4 ideias • 10–13", "📚  Papelaria & Utilitários\n5 ideias • 14–18"]
hdr_colors = ["2A5E4F","E87D6B","3A8A6E","9B6B4A"]
row = tbl.rows[0]
for i, h in enumerate(headers):
    c = row.cells[i]
    set_cell_shading(c, hdr_colors[i])
    p = c.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run(h)
    r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
    r.font.size = Pt(7.5)
    r.bold = True
    r.font.name = 'Calibri'
# segunda linha
row2 = tbl.add_row()
descs = [
    "Leve, barato,\nideal para deixar\nem massa",
    "Lembrança que\nfica na mesa,\nalto impacto visual",
    "Afeto no frio\nserrano, sabor\nque acolhe",
    "Dura meses,\nacompanha leitura\ne estudo"
]
for i, d in enumerate(descs):
    c = row2.cells[i]
    set_cell_shading(c, "FDF6EE" if i%2==0 else "FFF9F5")
    p = c.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(d)
    r.font.size = Pt(7)
    r.font.color.rgb = CINZA_TEXTO
    r.italic = True

document.add_paragraph().paragraph_format.space_after = Pt(2)
add_para("Dica de Lages: combine 1 cartão (01) + 1 mimo quentinho (10) no mesmo saquinho — custo total ~R$ 0,75 e experiência completa.", italic=True, size=7.5, color=RGBColor(0x7A,0x6A,0x4F), align=WD_ALIGN_PARAGRAPH.CENTER)

# --- ITERAR CATEGORIAS E IDEIAS ---

for cat in categorias:
    style_heading(document, cat["nome"], 1, VERDE_SERRA, 11)
    # linha colorida
    p = document.add_paragraph()
    pPr = p._p.get_or_add_pPr()
    pBdr = OxmlElement('w:pBdr')
    pPr.append(pBdr)
    bottom = OxmlElement('w:bottom')
    bottom.set(qn('w:val'), 'single')
    bottom.set(qn('w:sz'), '6')
    bottom.set(qn('w:space'), '1')
    bottom.set(qn('w:color'), cat["cor"])
    pBdr.append(bottom)
    p.paragraph_format.space_after = Pt(6)

    for ideia in cat["ideias"]:
        # Card ideia - tabela 1 col com header colorido
        tbl = document.add_table(rows=2, cols=1)
        tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        # Header
        hdr = tbl.cell(0,0)
        set_cell_shading(hdr, cat["cor"])
        # Ajustar altura?
        p = hdr.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_after = Pt(1)
        p.paragraph_format.space_before = Pt(3)
        r = p.add_run(f"  IDEA {ideia['id']}  —  {ideia['nome'].upper()}")
        r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
        r.font.size = Pt(8.5)
        r.bold = True
        r.font.name = 'Calibri'
        # badge dificuldade à direita - adicionar segunda linha no header?
        # vamos adicionar segundo parágrafo no header para dificuldade
        p2 = hdr.add_paragraph()
        p2.alignment = WD_ALIGN_PARAGRAPH.LEFT
        r = p2.add_run(f"   {ideia['dificuldade']}   •   {ideia['tempo']}   •   {ideia['custo']}")
        r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
        r.font.size = Pt(6.5)
        r.font.name = 'Calibri'

        # Body
        body = tbl.cell(1,0)
        # border
        tblPr = tbl._tbl.tblPr
        # already has borders? add
        tblBorders = OxmlElement('w:tblBorders')
        for bn in ['top','left','bottom','right','insideH','insideV']:
            b = OxmlElement(f'w:{bn}')
            b.set(qn('w:val'), 'single'); b.set(qn('w:sz'), '4'); b.set(qn('w:space'), '0'); b.set(qn('w:color'), cat["cor"])
            tblBorders.append(b)
        # Add only if not exists
        # Append
        try:
            tbl._tbl.tblPr.append(tblBorders)
        except:
            pass
        set_cell_shading(body, "FFFFFF")
        # Conteúdo body
        # Descrição
        p = body.paragraphs[0]
        p.paragraph_format.space_after = Pt(3)
        r = p.add_run("O que é: ")
        r.bold = True; r.font.size = Pt(7.5); r.font.color.rgb = VERDE_SERRA
        r2 = p.add_run(ideia["descricao"])
        r2.font.size = Pt(7.5); r2.font.color.rgb = CINZA_TEXTO

        # Grid 2 colunas internas - criar tabela 1x2 dentro do body
        inner = body.add_table(rows=1, cols=2)
        inner.alignment = WD_TABLE_ALIGNMENT.CENTER
        inner.autofit = True
        # Col1 Materiais
        c1 = inner.cell(0,0)
        c1.width = Inches(3.3)
        p = c1.paragraphs[0]
        p.paragraph_format.space_after = Pt(1)
        r = p.add_run("🧵 Materiais")
        r.bold = True; r.font.size = Pt(7); r.font.color.rgb = VERDE_SERRA
        p2 = c1.add_paragraph()
        p2.paragraph_format.space_after = Pt(1)
        r = p2.add_run(ideia["materiais"])
        r.font.size = Pt(7); r.font.color.rgb = CINZA_TEXTO
        # Col2 Texto
        c2 = inner.cell(0,1)
        p = c2.paragraphs[0]
        r = p.add_run("📖 Texto sugerido")
        r.bold = True; r.font.size = Pt(7); r.font.color.rgb = VERDE_SERRA
        p2 = c2.add_paragraph()
        p2.paragraph_format.space_after = Pt(1)
        # italic highlight
        r = p2.add_run(ideia["texto_sugestivo"])
        r.italic = True; r.font.size = Pt(7); r.font.color.rgb = RGBColor(0x6B,0x4A,0x4A)
        # Add shading to inner cells?
        set_cell_shading(c1, "F7F3EE")
        set_cell_shading(c2, "F0F7F3")

        # Dica montagem
        p = body.add_paragraph()
        p.paragraph_format.space_before = Pt(4)
        p.paragraph_format.space_after = Pt(1)
        pPr = p._p.get_or_add_pPr()
        shd = OxmlElement('w:shd')
        shd.set(qn('w:val'), 'clear'); shd.set(qn('w:color'), 'auto'); shd.set(qn('w:fill'), 'FDF6EE')
        pPr.append(shd)
        r = p.add_run("✂️ Dica de montagem: ")
        r.bold = True; r.font.size = Pt(7); r.font.color.rgb = RGBColor(0x9B,0x6B,0x4A)
        r2 = p.add_run(ideia["dica_montagem"])
        r2.font.size = Pt(7); r2.font.color.rgb = CINZA_TEXTO

        # Inspiração miimoska
        p = body.add_paragraph()
        p.paragraph_format.space_after = Pt(2)
        r = p.add_run("↳ Inspiração miimoska_: ")
        r.bold = True; r.font.size = Pt(6.5); r.font.color.rgb = PESSEGO
        r2 = p.add_run(ideia["inspiracao"] + "  •  Peça o arquivo pelo catálogo wa.me/c/557183611110")
        r2.font.size = Pt(6.5); r2.font.color.rgb = CINZA_CLARO; r2.italic = True

        # Espaço após card
        document.add_paragraph().paragraph_format.space_after = Pt(4)

# --- TABELA COMPARATIVA ---
style_heading(document, "Tabela comparativa — custo × tempo × impacto", 1, VERDE_SERRA, 13)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 1)
add_para("Use esta tabela para decidir em 1 minuto o que produzir conforme seu orçamento e equipe disponível em Lages.", size=7.5, italic=True, color=CINZA_CLARO)

tbl = document.add_table(rows=1, cols=5)
tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
tbl.autofit = False
tbl.columns[0].width = Inches(0.6)
tbl.columns[1].width = Inches(2.4)
tbl.columns[2].width = Inches(1.2)
tbl.columns[3].width = Inches(1.2)
tbl.columns[4].width = Inches(1.4)
hdr = tbl.rows[0].cells
headers = ["ID", "Ideia", "Custo/unid.", "Tempo", "Impacto*"]
for i, h in enumerate(headers):
    c = hdr[i]
    set_cell_shading(c, "2A5E4F")
    p = c.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(h)
    r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
    r.font.size = Pt(7)
    r.bold = True

dados_tabela = [
    ("01","Cartão Semente","R$ 0,18","30/h","★★★ Alto"),
    ("02","Marcador Magnético","R$ 0,35","40/h","★★★ Alto"),
    ("03","Card + Bala + QR","R$ 0,40","50/h","★★☆ Médio"),
    ("04","Envelope Carta","R$ 0,55","25/h","★★★ Alto"),
    ("05","Caixinha Milk","R$ 3,00","20/h","★★★ Alto"),
    ("06","Caixinha Sushi Brownie","R$ 3,00","18/h","★★★ Alto"),
    ("07","Bolsinha Casinha (chá)","R$ 1,50","22/h","★★☆ Médio"),
    ("08","Travesseirinho","R$ 1,25","35/h","★★☆ Médio"),
    ("09","Cubo 6×6","R$ 2,60","20/h","★★☆ Médio"),
    ("10","Solapa Chá+Choc","R$ 1,20","45/h","★★★ Alto"),
    ("11","Rótulo Pirulito","R$ 0,55","60/h","★☆☆ Rápido"),
    ("12","Porta Bis Duplo","R$ 1,25","50/h","★★☆ Médio"),
    ("13","Tag Garrafinha","R$ 0,85","55/h","★★☆ Médio"),
    ("14","Bloquinho","R$ 1,60","15/h","★★★ Alto"),
    ("15","Sachê Perfumado","R$ 1,60","30/h","★★☆ Médio"),
    ("16","Imã Calendário","R$ 0,55","30/h","★★★ Alto"),
    ("17","Régua Marcador","R$ 0,28","40/h","★★★ Alto"),
    ("18","Kit 3 em 1","R$ 3,00","15/h","★★★ Alto"),
]
for row_data in dados_tabela:
    row = tbl.add_row().cells
    for i, val in enumerate(row_data):
        c = row[i]
        p = c.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER if i!=1 else WD_ALIGN_PARAGRAPH.LEFT
        r = p.add_run(val)
        r.font.size = Pt(6.5)
        r.font.color.rgb = CINZA_TEXTO
        if i==0:
            r.bold = True
            r.font.color.rgb = VERDE_SERRA
        # shading zebra
        idx = dados_tabela.index(row_data)
        if idx %2==0:
            set_cell_shading(c, "FDF6EE")
        else:
            set_cell_shading(c, "FFFFFF")

add_para("*Impacto = tempo que o morador guarda a lembrança + chance de gerar conversa. Baseado em relatos de campanhas anteriores.", size=6, italic=True, color=CINZA_CLARO, space_before=2)

# --- PLANO DE AÇÃO LAGES ---
style_heading(document, "Plano de ação para Lages — clima, logística e estoque", 1, VERDE_SERRA, 13)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 1)
add_para("Lages em campanha (inverno/primavera): manhãs frias (5–12 °C), neblina e comércio central aquecido. A estratégia deve ser 'quente e leve'.", size=8, italic=True, color=RGBColor(0x6B,0x4A,0x4A))

# 3 colunas plano
tbl = document.add_table(rows=1, cols=3)
tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
headers = ["🧥  Semana 1 — Aquecer", "📍  Semana 2 — Centro", "🏡  Semana 3–4 — Revisitas"]
colors = ["2A5E4F","E87D6B","3A8A6E"]
for i, h in enumerate(headers):
    c = tbl.rows[0].cells[i]
    set_cell_shading(c, colors[i])
    p = c.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(h)
    r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
    r.font.size = Pt(7.5)
    r.bold = True
row = tbl.add_row()
conteudos = [
    "Entrega em massa de 01 + 02 nas casas (08h–11h, frio ainda). Equipe de 4 faz 500 cartõezinhos/semana. Custo: R$ 90.",
    "Ação no Calçadão e praças: 03 + 11 + 13. QR code leva ao vídeo da campanha. 300 unidades. Custo: R$ 180. Alta rotatividade.",
    "Visitas marcadas: 10 + 14 + 18. Chá + bloquinho criam vínculo. 80 kits. Custo: R$ 160. Ideal para estudos."
]
for i, txt in enumerate(conteudos):
    c = row.cells[i]
    p = c.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(txt)
    r.font.size = Pt(7)
    r.font.color.rgb = CINZA_TEXTO
    set_cell_shading(c, "FDF6EE" if i%2==0 else "FFF9F5")

add_para("Estoque sugerido para 1000 moradias: 600 cartões (01), 400 marcadores (02), 300 balas QR (03), 150 solapas chá (10), 80 caixinhas milk (05) e 50 kits (18). Total estimado: R$ 620–780 + impressão.", size=7.5, color=CINZA_TEXTO, space_before=6)
add_para("Dica serrana: ofereça o chá dizendo 'Um chazinho para aquecer — e uma mensagem que aquece o coração'. Funciona muito em Lages em dias de geada.", italic=True, size=7.5, color=RGBColor(0x9B,0x6B,0x4A))

# --- GUIA IMPRESSÃO ---
style_heading(document, "Guia de impressão e montagem — padrão miimoska_", 1, VERDE_SERRA, 13)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 1)

tbl = document.add_table(rows=1, cols=2)
tbl.columns[0].width = Inches(3.5)
tbl.columns[1].width = Inches(3.5)
# Papéis
c1 = tbl.cell(0,0)
set_cell_shading(c1, "F0F7F3")
p = c1.paragraphs[0]
r = p.add_run("📄  Papéis recomendados")
r.bold = True; r.font.size = Pt(8); r.font.color.rgb = VERDE_SERRA
items = [
    "Cartões/marcadores: Offset 180g ou Fotográfico 180g (fosco) — 9 por folha",
    "Caixinhas: Couchê 230–300g ou Supremo 250g — vinco obrigatório",
    "Envelopes: Kraft 120g ou Offset 120g — impressão só frente",
    "Tags/solapas: Couchê 180g — corte com guilhotina",
]
for it in items:
    p = c1.add_paragraph(style='List Bullet')
    r = p.add_run(it)
    r.font.size = Pt(7); r.font.color.rgb = CINZA_TEXTO

c2 = tbl.cell(0,1)
set_cell_shading(c2, "FDF6EE")
p = c2.paragraphs[0]
r = p.add_run("✂️  Ferramentas & acabamento")
r.bold = True; r.font.size = Pt(8); r.font.color.rgb = RGBColor(0x9B,0x6B,0x4A)
items2 = [
    "Guilhotina A4 + estilete + régua de metal + base de corte",
    "Cola em bastão (card) e dupla face 12mm (caixinhas)",
    "Cantoneira para arredondar cantos (marcadores/réguas)",
    "Fita cetim nº1 (0,7cm) e nº2 (1,5cm) — tons terrosos",
    "Manta magnética adesiva em rolo (marcadores/imãs)",
    "Laminado fosco opcional para durabilidade (Lages úmida)",
]
for it in items2:
    p = c2.add_paragraph(style='List Bullet')
    r = p.add_run(it)
    r.font.size = Pt(7); r.font.color.rgb = CINZA_TEXTO

add_para("Impressão: jato de tinta com tinta pigmentada ou laser colorida. Sempre em 'qualidade alta' e 'sem margem'. Faça prova em sulfite antes. Arquivos miimoska_ já vêm em PDF com marcas de corte e vinco — é só imprimir frente (e verso quando indicado) e cortar.", size=7, italic=True, color=CINZA_CLARO, space_before=4)

# Fluxo montagem
p = document.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(4)
r = p.add_run("FLUXO DE MONTAGEM EM MUTIRÃO (modelo testado pela @miimoska_):  ")
r.bold = True; r.font.size = Pt(7); r.font.color.rgb = VERDE_SERRA
r2 = p.add_run("1. Corte tudo → 2. Vinque tudo → 3. Cole tudo → 4. Recheie → 5. Feche com fita/tag")
r2.font.size = Pt(7); r2.font.color.rgb = CINZA_TEXTO
add_para("Nunca faça 1 unidade completa por vez — produza em série por etapa. 4 pessoas em 2 horas montam ~120 caixinhas ou 300 cartões.", size=7, italic=True, color=CINZA_CLARO, align=WD_ALIGN_PARAGRAPH.CENTER)

# --- CHECKLIST ---
style_heading(document, "Checklist de compras & fornecedores", 1, VERDE_SERRA, 13)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 1)

tbl = document.add_table(rows=1, cols=3)
tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
hdr = tbl.rows[0].cells
headers = ["Item", "Quantidade p/ 500 unid.", "Onde comprar em Lages / Online"]
for i, h in enumerate(headers):
    c = hdr[i]
    set_cell_shading(c, "2A5E4F")
    p = c.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(h)
    r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
    r.font.size = Pt(7)
    r.bold = True

check = [
    ("Papel Offset 180g A4", "120 folhas (1 pct)", "Kalunga, papelarias centro, Mercado Livre"),
    ("Couchê 230g A4", "80 folhas", "Gráfica rápida Lages (Av. Duque de Caxias)"),
    ("Manta magnética 30cm", "2 metros (rolo)", "Shopee / Casa do ímã"),
    ("Fita cetim", "3 rolos cores outono", "Armarinhos Fernando, Lojas de festa"),
    ("Balas / Bis / Pirulitos", "300 unid. cada", "Atacadão, Festval"),
    ("Chás sazonais", "100 sachês", "Mercado + Empório Serra (chá serrano)"),
    ("Brownie / pão mel", "80–100 unid.", "Padaria Serra, confeiteira local"),
    ("Saquinho zip 7×10", "200 unid.", "Embalagens Lages / Shopee"),
    ("Arquivos miimoska_", "1 compra por modelo", "wa.me/c/557183611110 • t.me/miimoska"),
]
for item, qtd, onde in check:
    row = tbl.add_row().cells
    for i, val in enumerate([item, qtd, onde]):
        p = row[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        r = p.add_run(val)
        r.font.size = Pt(6.5)
        r.font.color.rgb = CINZA_TEXTO
        if i==0:
            r.bold = True

add_para("💰 Orçamento enxuto (só cartões + marcadores): R$ 120 para 500 famílias. Orçamento completo (com caixinhas e kits): R$ 700 para 500 famílias — R$ 1,40 por lar.", bold=True, size=8, color=VERDE_SERRA, space_before=4, align=WD_ALIGN_PARAGRAPH.CENTER)

# --- TEXTOS BÍBLICOS ---
style_heading(document, "Textos bíblicos sugeridos para a campanha", 1, VERDE_SERRA, 13)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 1)
add_para("Sugestões de versículos curtos (fáceis de ler no frio, na porta) para cada tipo de lembrancinha. Todos com referência para o morador conferir.", size=7.5, italic=True, color=CINZA_CLARO)

tbl = document.add_table(rows=1, cols=2)
tbl.columns[0].width = Inches(3.5)
tbl.columns[1].width = Inches(3.5)
# Col esq
c1 = tbl.cell(0,0)
set_cell_shading(c1, "F0F7F3")
p = c1.paragraphs[0]
r = p.add_run("🌱  Para cartões e marcadores")
r.bold = True; r.font.size = Pt(7.5); r.font.color.rgb = VERDE_SERRA
vers1 = [
    "Isaías 55:11 — palavra que não volta vazia",
    "Salmo 1:2 — prazer na lei de Jeová",
    "Apocalipse 21:4 — sem dor nem lágrimas",
    "João 17:3 — conhecer a Deus",
]
for v in vers1:
    p = c1.add_paragraph(style='List Bullet')
    r = p.add_run(v)
    r.font.size = Pt(7); r.font.color.rgb = CINZA_TEXTO
c2 = tbl.cell(0,1)
set_cell_shading(c2, "FDF6EE")
p = c2.paragraphs[0]
r = p.add_run("🍵  Para mimos quentinhos e caixinhas")
r.bold = True; r.font.size = Pt(7.5); r.font.color.rgb = RGBColor(0x9B,0x6B,0x4A)
vers2 = [
    "Provérbios 16:24 — palavras como mel",
    "Isaías 41:10 — não tenha medo",
    "Salmo 34:8 — provem e vejam",
    "Romanos 12:12 — alegrem-se na esperança",
]
for v in vers2:
    p = c2.add_paragraph(style='List Bullet')
    r = p.add_run(v)
    r.font.size = Pt(7); r.font.color.rgb = CINZA_TEXTO

add_para("Dica: no verso do cartão, deixe espaço em branco com 'Anote aqui sua pergunta bíblica favorita:' — cria ponte para revisita sem pressão.", size=7, italic=True, color=RGBColor(0x7A,0x6A,0x4F), space_before=4)

# --- PRÓXIMOS PASSOS ---
style_heading(document, "Próximos passos", 1, VERDE_SERRA, 13)
add_horizontal_line(document.add_paragraph(), "E8D9C0", 1)
passos = [
    "1. Escolha 3 ideias (1 barata + 1 caixinha + 1 quentinha). Ex.: 01 + 05 + 10.",
    "2. Peça os arquivos à @miimoska_ pelo catálogo (wa.me/c/557183611110) — informe o nome exato (ex.: 'Caixa Milk', 'Porta Bis', 'Solapa'). Valor médio R$ 8–15 por arquivo, imprime ilimitado.",
    "3. Faça 5 provas em casa, ajuste cores (Lages pede tons outono: verde araucária, terracota, bege, pêssego).",
    "4. Marque mutirão de 2h pós-reunião — 6 pessoas = 400 unidades.",
    "5. Entregue com sorriso e frase curta: 'Trouxemos um mimo quentinho e uma mensagem que aquece o coração — será que podemos voltar para conversar 5 minutinhos?'",
]
for passo in passos:
    p = document.add_paragraph(style='List Bullet')
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(passo)
    r.font.size = Pt(8)
    r.font.color.rgb = CINZA_TEXTO
    if passo.startswith("1.") or passo.startswith("5."):
        r.bold = True

# Agradecimento box
tbl = document.add_table(rows=1, cols=1)
c = tbl.cell(0,0)
set_cell_shading(c, "2A5E4F")
p = c.paragraphs[0]
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(6)
r = p.add_run("Obrigado por levar esperança a Lages — com capricho, carinho e criatividade!  🌵✂️💛")
r.font.color.rgb = RGBColor(0xFF,0xFF,0xFF)
r.font.size = Pt(9)
r.bold = True
r.italic = True
p2 = c.add_paragraph()
p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p2.add_run("Inspirado no trabalho da Karine França (@miimoska_) — 'Imprima, monte e venda' •  Produção física liberada\nQue Jeová abençoe cada lar alcançado na campanha. — Coletânea preparada em agosto de 2026")
r.font.color.rgb = RGBColor(0xD9,0xE8,0xE0)
r.font.size = Pt(6.5)

# Rodapé final
document.add_paragraph().paragraph_format.space_after = Pt(2)
p = document.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("—  FIM  —\nDúvidas? Fale com a @miimoska_ no direct ou WhatsApp 71 98361-1110 • Canal Telegram: t.me/miimoska")
r.font.size = Pt(6)
r.font.color.rgb = CINZA_CLARO
r.italic = True

# Salvar
document.save(WORD_PATH)
print(f"Word salvo em {WORD_PATH}")

