#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
import os

OUT_DIR = "/Volumes/M2/Users/tarciso/Downloads/OpenApsAIMI-master_Plugin_OpenApsAIMI_Steps-DEV-2"
PPT_PATH = os.path.join(OUT_DIR, "Apresentacao_Coletanea_Lages_Miimoska.pptx")

prs = Presentation()
prs.slide_width = Inches(13.33)
prs.slide_height = Inches(7.5)

# Paleta
VERDE = RGBColor(0x2A, 0x5E, 0x4F)
VERDE_CLARO = RGBColor(0x3A, 0x8A, 0x6E)
PESSEGO = RGBColor(0xE8, 0x7D, 0x6B)
BEGE = RGBColor(0xF5, 0xE6, 0xD3)
BEGE_BG = RGBColor(0xFD, 0xF6, 0xEE)
CINZA = RGBColor(0x4A, 0x4A, 0x4A)
CINZA_CLARO = RGBColor(0x8A, 0x8A, 0x8A)
BRANCO = RGBColor(0xFF, 0xFF, 0xFF)

def set_bg(slide, color):
    bg = slide.background
    fill = bg.fill
    fill.solid()
    fill.fore_color.rgb = color

def add_shape(slide, left, top, width, height, fill_color=None, line_color=None, radius=None):
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE, left, top, width, height)
    shape.line.fill.background()
    if fill_color:
        shape.fill.solid()
        shape.fill.fore_color.rgb = fill_color
    else:
        shape.fill.background()
    if line_color:
        shape.line.color.rgb = line_color
        shape.line.width = Pt(1)
    if radius:
        shape.adjustments[0] = 0.08
    return shape

def add_text_box(slide, left, top, width, height, text, font_size=12, bold=False, color=CINZA, alignment=PP_ALIGN.LEFT, font_name="Calibri", italic=False):
    txBox = slide.shapes.add_textbox(left, top, width, height)
    tf = txBox.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = text
    p.font.size = Pt(font_size)
    p.font.bold = bold
    p.font.color.rgb = color
    p.font.name = font_name
    p.font.italic = italic
    p.alignment = alignment
    return txBox

def add_multi_text(slide, left, top, width, height, parts, line_spacing=1.0):
    txBox = slide.shapes.add_textbox(left, top, width, height)
    tf = txBox.text_frame
    tf.word_wrap = True
    for i, part in enumerate(parts):
        if i == 0:
            p = tf.paragraphs[0]
        else:
            p = tf.add_paragraph()
        p.text = part["text"]
        p.font.size = Pt(part.get("size", 10))
        p.font.bold = part.get("bold", False)
        p.font.italic = part.get("italic", False)
        p.font.color.rgb = part.get("color", CINZA)
        p.font.name = part.get("font", "Calibri")
        p.alignment = part.get("align", PP_ALIGN.LEFT)
        p.line_spacing = Pt(part.get("size", 10) * line_spacing)
        p.space_after = Pt(part.get("space_after", 2))
        p.space_before = Pt(part.get("space_before", 0))
    return txBox

# SLIDE 1 - CAPA
slide = prs.slides.add_slide(prs.slide_layouts[6]) # blank
set_bg(slide, BEGE_BG)
# faixa topo verde
shape = add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.45), fill_color=VERDE)
add_text_box(slide, Inches(0.5), Inches(0.1), Inches(12.33), Inches(0.25),
             "✦  COLETÂNEA TEOCRÁTICA  •  INSPIRADA EM @MIIMOSKA_  (KARINE FRANÇA)  •  IMPRIMA, MONTE ✂️ E VENDA  ✦",
             font_size=7, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER, font_name="Calibri")
# titulo
add_text_box(slide, Inches(0.7), Inches(1.0), Inches(11.9), Inches(0.8),
             "COLETÂNEA DE IDEIAS", font_size=38, bold=True, color=VERDE, alignment=PP_ALIGN.CENTER, font_name="Calibri Light")
add_text_box(slide, Inches(0.7), Inches(1.75), Inches(11.9), Inches(0.85),
             "DE LEMBRANCINHAS", font_size=38, bold=True, color=PESSEGO, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(0.7), Inches(2.75), Inches(11.9), Inches(0.4),
             "para Campanha de Pregação em LAGES  •  Serra Catarinense", font_size=14, bold=False, color=CINZA, alignment=PP_ALIGN.CENTER, italic=True)
# linha
shape = add_shape(slide, Inches(5.9), Inches(3.25), Inches(1.5), Inches(0.03), fill_color=RGBColor(0xC8,0xC8,0xC8))
add_text_box(slide, Inches(6.3), Inches(3.35), Inches(0.7), Inches(0.25), "✂", font_size=12, color=CINZA_CLARO, alignment=PP_ALIGN.CENTER)
# box bege destaque
shape = add_shape(slide, Inches(3.0), Inches(3.85), Inches(7.3), Inches(0.9), fill_color=RGBColor(0xFF,0xFF,0xFF), line_color=RGBColor(0xE8,0xD9,0xC0), radius=True)
add_text_box(slide, Inches(3.2), Inches(4.0), Inches(6.9), Inches(0.25),
             "18 ideias práticas  •  R$ 0,15 a R$ 3,00 por unidade  •  Produção física liberada", font_size=9, bold=True, color=VERDE, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(3.2), Inches(4.32), Inches(6.9), Inches(0.3),
             "Estilo 'imprima e monte'  •  277 posts  •  6,6k seguidores  •  Nordestina, sim senhor 🌵", font_size=7.5, bold=False, color=CINZA, alignment=PP_ALIGN.CENTER, italic=True)
# 3 cards inferior
cards = [
    (VERDE, "❄️  LAGES", "Inverno acolhedor\nChás & araucárias", BRANCO),
    (PESSEGO, "⏱  TEMPO", "15 a 60 unid./hora\nMutirão pós-reunião", BRANCO),
    (BEGE, "💰  CUSTO", "Cabe em qualquer\norçamento", VERDE),
]
for i, (col, title, desc, txtcol) in enumerate(cards):
    left = Inches(2.2 + i*3.1)
    shape = add_shape(slide, left, Inches(5.15), Inches(2.7), Inches(1.1), fill_color=col, radius=True)
    # line
    if col == BEGE:
        shape.line.color.rgb = RGBColor(0xE8,0xD9,0xC0)
        shape.line.width = Pt(1)
    add_text_box(slide, left, Inches(5.3), Inches(2.7), Inches(0.3), title, font_size=9, bold=True, color=txtcol, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, left+Inches(0.2), Inches(5.65), Inches(2.3), Inches(0.5), desc, font_size=7.5, bold=False, color=txtcol if txtcol==BRANCO else CINZA, alignment=PP_ALIGN.CENTER)

add_text_box(slide, Inches(0.5), Inches(6.9), Inches(12.33), Inches(0.2),
             "Agosto 2026  •  instagram.com/miimoska_  •  wa.me/c/557183611110  •  t.me/miimoska", font_size=6, color=CINZA_CLARO, alignment=PP_ALIGN.CENTER, italic=True)

# SLIDE 2 - SUMÁRIO / COMO USAR
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, BRANCO)
add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.9), fill_color=VERDE)
add_text_box(slide, Inches(0.5), Inches(0.2), Inches(7), Inches(0.35), "SUMÁRIO & COMO USAR", font_size=22, bold=True, color=BRANCO, alignment=PP_ALIGN.LEFT)
add_text_box(slide, Inches(0.5), Inches(0.5), Inches(7), Inches(0.2), "Seu mapa de 15 minutos para escolher, imprimir e montar", font_size=9, color=RGBColor(0xD9,0xE8,0xE0), alignment=PP_ALIGN.LEFT, italic=True)
# left list
items_sum = [
    ("01", "Visão geral — 4 categorias", "18 ideias organizadas por custo e tempo"),
    ("02", "Cartões & Marcadores", "Ideias 01–04 • R$0,18–0,55 • massa"),
    ("03", "Caixinhas & Embalagens", "Ideias 05–09 • R$1,25–3,00 • ficam na mesa"),
    ("04", "Doces & Mimos quentinhos", "Ideias 10–13 • R$0,55–1,20 • inverno de Lages"),
    ("05", "Papelaria & Utilitários", "Ideias 14–18 • R$0,28–3,00 • duram meses"),
    ("06", "Tabela comparativa", "Custo × tempo × impacto em 1 página"),
    ("07", "Plano de ação Lages", "Cronograma 4 semanas + estoque 1000 lares"),
    ("08", "Guia de impressão", "Papéis, cortes e fluxo mutirão miimoska_"),
]
for idx, (num, title, desc) in enumerate(items_sum):
    y = Inches(1.25 + idx*0.68)
    # number badge
    shape = add_shape(slide, Inches(0.5), y, Inches(0.45), Inches(0.45), fill_color=VERDE if idx<4 else PESSEGO, radius=True)
    add_text_box(slide, Inches(0.5), y+Inches(0.08), Inches(0.45), Inches(0.3), num, font_size=8, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, Inches(1.05), y+Inches(0.02), Inches(5.5), Inches(0.2), title, font_size=9, bold=True, color=VERDE if idx<4 else CINZA, alignment=PP_ALIGN.LEFT)
    add_text_box(slide, Inches(1.05), y+Inches(0.22), Inches(5.5), Inches(0.2), desc, font_size=7, color=CINZA_CLARO, alignment=PP_ALIGN.LEFT)

# right box destaque
shape = add_shape(slide, Inches(7.6), Inches(1.2), Inches(5.0), Inches(5.5), fill_color=BEGE_BG, line_color=RGBColor(0xE8,0xD9,0xC0), radius=True)
add_text_box(slide, Inches(7.9), Inches(1.4), Inches(4.4), Inches(0.3), "💡  COMO ESCOLHER EM 1 MINUTO", font_size=9, bold=True, color=VERDE, alignment=PP_ALIGN.LEFT)
tips = [
    "✅  Campanha curta? → Cartões 01–04 (R$0,18, 30/h)",
    "✅  Quer que fique na casa? → Caixinhas 05–09",
    "✅  Frio serrano? → Mimos quentinhos 08, 10, 13",
    "✅  Estudos/revisitas? → Utilitários 14–18",
]
for i, tip in enumerate(tips):
    add_text_box(slide, Inches(7.9), Inches(1.85 + i*0.35), Inches(4.4), Inches(0.3), tip, font_size=7.5, color=CINZA, alignment=PP_ALIGN.LEFT)
# aviso direitos
add_text_box(slide, Inches(7.9), Inches(3.4), Inches(4.4), Inches(0.25), "⚠️  Transparência:", font_size=7, bold=True, color=RGBColor(0x8B,0x3A,0x3A), alignment=PP_ALIGN.LEFT)
add_text_box(slide, Inches(7.9), Inches(3.65), Inches(4.4), Inches(0.7), "As artes originais são pagas e protegidas. Esta coletânea inspira-se nos formatos (milk, porta bis, solapa…) para você saber o que pedir no catálogo oficial da @miimoska_. Sempre compre o PDF antes de imprimir.", font_size=6.5, color=RGBColor(0x6B,0x4A,0x4A), alignment=PP_ALIGN.LEFT)
# flux mutirão
shape = add_shape(slide, Inches(7.9), Inches(4.65), Inches(4.4), Inches(0.7), fill_color=RGBColor(0x2A,0x5E,0x4F), radius=True)
add_text_box(slide, Inches(8.0), Inches(4.75), Inches(4.2), Inches(0.2), "FLUXO MUTIRÃO  (padrão miimoska_)", font_size=7, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(8.0), Inches(5.0), Inches(4.2), Inches(0.25), "Corte tudo  →  Vinque tudo  →  Cole tudo  →  Recheie", font_size=7, color=BRANCO, alignment=PP_ALIGN.CENTER)

add_text_box(slide, Inches(7.9), Inches(5.6), Inches(4.4), Inches(0.5), "4 pessoas em 2 horas:\n120 caixinhas  ou  300 cartões", font_size=8, bold=True, color=VERDE, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(7.9), Inches(6.15), Inches(4.4), Inches(0.2), "Produção em série por etapa, nunca 1 por vez.", font_size=6.5, color=CINZA_CLARO, alignment=PP_ALIGN.CENTER, italic=True)

# SLIDE 3 - VISÃO 4 CATEGORIAS
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, BRANCO)
add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.7), fill_color=VERDE)
add_text_box(slide, Inches(0.5), Inches(0.15), Inches(12), Inches(0.4), "VISÃO GERAL — 4 CATEGORIAS • 18 IDEIAS", font_size=18, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
# 4 big cards
cats = [
    (VERDE, "📮", "CARTÕES &\nMARCADORES", "4 ideias • 01–04", "Leve, barato\nmassa", "R$ 0,18–0,55", "30–60/h", "Ex.: Cartão Semente\nMarcador Magnético"),
    (PESSEGO, "📦", "CAIXINHAS &\nEMBALAGENS", "5 ideias • 05–09", "Fica na mesa\nalto impacto", "R$ 1,25–3,00", "18–35/h", "Ex.: Milk, Sushi\nTravesseirinho"),
    (VERDE_CLARO, "🍫", "DOCES &\nQUENTINHOS", "4 ideias • 10–13", "Afeto no frio\nsabor acolhe", "R$ 0,55–1,20", "45–60/h", "Ex.: Chá+Choc\nPirulito, Porta Bis"),
    (RGBColor(0x9B,0x6B,0x4A), "📚", "PAPELARIA &\nUTILITÁRIOS", "5 ideias • 14–18", "Dura meses\nacompanha leitura", "R$ 0,28–3,00", "15–40/h", "Ex.: Bloquinho\nImã, Régua"),
]
for i, (col, icon, title, subt, desc, custo, tempo, ex) in enumerate(cats):
    left = Inches(0.6 + i*3.2)
    # card bg
    shape = add_shape(slide, left, Inches(1.05), Inches(2.8), Inches(5.8), fill_color=RGBColor(0xFF,0xFF,0xFF), line_color=col, radius=True)
    # top color bar
    topbar = add_shape(slide, left, Inches(1.05), Inches(2.8), Inches(1.0), fill_color=col, radius=True)
    # need to cut bottom radius - just overlay white shape? keep simple
    add_text_box(slide, left, Inches(1.15), Inches(2.8), Inches(0.35), icon, font_size=18, color=BRANCO, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, left+Inches(0.2), Inches(1.55), Inches(2.4), Inches(0.5), title, font_size=9, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, left, Inches(2.05), Inches(2.8), Inches(0.2), subt, font_size=6.5, color=BRANCO, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, left+Inches(0.2), Inches(2.45), Inches(2.4), Inches(0.5), desc, font_size=7.5, color=CINZA, alignment=PP_ALIGN.CENTER, italic=True)
    # custos
    shape2 = add_shape(slide, left+Inches(0.2), Inches(3.15), Inches(2.4), Inches(0.55), fill_color=BEGE_BG, radius=True)
    add_text_box(slide, left+Inches(0.2), Inches(3.2), Inches(1.15), Inches(0.2), "💰 " + custo, font_size=7, bold=True, color=VERDE, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, left+Inches(1.35), Inches(3.2), Inches(1.15), Inches(0.2), "⏱ " + tempo, font_size=7, bold=True, color=VERDE, alignment=PP_ALIGN.CENTER)
    # exemplo
    add_text_box(slide, left+Inches(0.2), Inches(3.85), Inches(2.4), Inches(0.6), ex, font_size=7, color=CINZA_CLARO, alignment=PP_ALIGN.CENTER)
    # badge numero
    badge = add_shape(slide, left+Inches(1.05), Inches(4.6), Inches(0.7), Inches(0.25), fill_color=col)
    add_text_box(slide, left+Inches(1.05), Inches(4.62), Inches(0.7), Inches(0.2), f"IDEIAS {['01–04','05–09','10–13','14–18'][i]}", font_size=6, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)

add_text_box(slide, Inches(0.5), Inches(7.0), Inches(12.33), Inches(0.2),
             "Dica lageana: combine 1 cartão (01) + 1 mimo quentinho (10) no mesmo saquinho — R$0,75 experiência completa  •  'Um chazinho para aquecer e uma mensagem que aquece o coração'", font_size=7, color=RGBColor(0x9B,0x6B,0x4A), alignment=PP_ALIGN.CENTER, italic=True)

# SLIDES 4-7 : CATEGORIAS DETALHES - vamos criar slides por categoria, 2 colunas

def slide_categoria(num_slide, cat_title, cat_num, color, ideias_lista):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    set_bg(slide, BRANCO)
    # header
    add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.7), fill_color=color)
    add_text_box(slide, Inches(0.5), Inches(0.15), Inches(12), Inches(0.4), cat_title, font_size=16, bold=True, color=BRANCO, alignment=PP_ALIGN.LEFT)
    add_text_box(slide, Inches(10.5), Inches(0.2), Inches(2.3), Inches(0.3), cat_num, font_size=9, bold=True, color=BRANCO, alignment=PP_ALIGN.RIGHT)
    # ideias - 2 por linha
    for idx, ideia in enumerate(ideias_lista):
        col = idx % 2
        row = idx // 2
        left = Inches(0.4 + col*6.65)
        top = Inches(0.9 + row*2.05)
        # card
        shape = add_shape(slide, left, top, Inches(6.3), Inches(1.85), fill_color=BRANCO, line_color=color, radius=True)
        # id badge
        badge = add_shape(slide, left+Inches(0.15), top+Inches(0.12), Inches(0.65), Inches(0.3), fill_color=color, radius=True)
        add_text_box(slide, left+Inches(0.15), top+Inches(0.15), Inches(0.65), Inches(0.22), ideia["id"], font_size=7, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
        add_text_box(slide, left+Inches(0.9), top+Inches(0.12), Inches(3.8), Inches(0.22), ideia["nome"], font_size=7.5, bold=True, color=color, alignment=PP_ALIGN.LEFT)
        add_text_box(slide, left+Inches(4.8), top+Inches(0.12), Inches(1.35), Inches(0.22), ideia["meta"], font_size=6, color=CINZA_CLARO, alignment=PP_ALIGN.RIGHT)
        add_text_box(slide, left+Inches(0.15), top+Inches(0.45), Inches(6.0), Inches(0.45), ideia["desc"], font_size=6.5, color=CINZA, alignment=PP_ALIGN.LEFT)
        # footer mini
        # materiais
        add_text_box(slide, left+Inches(0.15), top+Inches(0.98), Inches(2.9), Inches(0.4), "🧵 " + ideia["mat"], font_size=5.5, color=CINZA_CLARO, alignment=PP_ALIGN.LEFT)
        add_text_box(slide, left+Inches(3.2), top+Inches(0.98), Inches(2.9), Inches(0.4), "📖 " + ideia["texto"], font_size=5.5, bold=False, color=RGBColor(0x6B,0x4A,0x4A), alignment=PP_ALIGN.LEFT, italic=True)
        add_text_box(slide, left+Inches(0.15), top+Inches(1.42), Inches(6.0), Inches(0.3), "↳ " + ideia["ref"], font_size=5, color=PESSEGO, alignment=PP_ALIGN.LEFT)
    return slide

slide_categoria(4, "CATEGORIA 1  —  CARTÕES & MARCADORES: leves para deixar em massa", "IDEIAS 01–04 • R$0,18–0,55", VERDE, [
    {"id":"01","nome":"CARTÃO SEMENTE 'Semeando Esperança'","meta":"★☆☆ • 30/h • R$0,18","desc":"8,5×6,5cm (9/folha), aquarela serra + Isaías 55:11. Envelope kraft. Papel semente plantável.","mat":"Offset 180g / papel semente 150g","texto":"Isa. 55:11 — palavra não volta vazia","ref":"miimoska_ | Kit Cartões + Envelope Coração"},
    {"id":"02","nome":"MARCADOR MAGNÉTICO 'Lages Acolhedora'","meta":"★☆☆ • 40/h • R$0,35","desc":"5×15cm dobrado com imã 10mm. Araucária + 'Que Jeová abençoe sua leitura'.","mat":"Fotográfico 230g + manta magnética","texto":"Salmo 1:2","ref":"miimoska_ | Marcadores Magnéticos"},
    {"id":"03","nome":"CARD + BALA + QR CODE","meta":"★☆☆ • 50/h • R$0,40","desc":"7×10cm com bala goma + QR jw.org. Perfeito para comércio central de Lages.","mat":"Couchê 250g + balinha + QR","texto":"'Venha ouvir as boas novas!' + endereço Salão","ref":"miimoska_ | Card Bombom / Cartão Bala"},
    {"id":"04","nome":"ENVELOPE 'CARTA DE ESPERANÇA' + FOLHETO","meta":"★★☆ • 25/h • R$0,55","desc":"Envelope air mail 10×7cm kraft + folheto A6 4 pág. Carimbo 'com carinho'.","mat":"Kraft 120g + sulfite 90g","texto":"Apoc. 21:4 — sem dor nem lágrimas","ref":"miimoska_ | Kit Cartas + Envelopes"},
])

slide_categoria(5, "CATEGORIA 2  —  CAIXINHAS & EMBALAGENS: lembrança que fica na mesa", "IDEIAS 05–09 • R$1,25–3,00", PESSEGO, [
    {"id":"05","nome":"CAIXINHA MILK 'Pão Diário' (pão de mel)","meta":"★★☆ • 20/h • R$3,00","desc":"7×7×10cm com visor acetato + tag João 6:35. Cabe pão de mel 30g / mini panetone.","mat":"Couchê 230g + acetato + cetim","texto":"João 6:35 — pão da vida","ref":"miimoska_ | Caixa Milk + Tag"},
    {"id":"06","nome":"CAIXINHA SUSHI + BROWNIE 'Doce Esperança'","meta":"★★☆ • 18/h • R$3,00","desc":"10×6×5cm kraft com coração vazado. Brownie 40g + cartão 5cm.","mat":"Kraft 250g + brownie","texto":"Salmo 34:8 — provem e vejam","ref":"miimoska_ | Caixinha Sushi"},
    {"id":"07","nome":"BOLSINHA CASINHA 'Lar com Jeová'","meta":"★★☆ • 22/h • R$1,50","desc":"9×9×4cm formato casinha, 2–3 sachês chá mate serrano (a cara de Lages).","mat":"Fotográfico 180g + chás","texto":"Josué 24:15","ref":"miimoska_ | Sacolinha Casinha"},
    {"id":"08","nome":"TRAVESSEIRINHO 'Abraço Quentinho'","meta":"★☆☆ • 35/h • R$1,25","desc":"10×12cm perolizado + escalda-pés (sal grosso+lavanda) ou 2 toffees. Inverno!","mat":"Perolizado 180g + sal aromático","texto":"Isaías 41:10 — estou com você","ref":"miimoska_ | Travesseirinho"},
    {"id":"09","nome":"CUBO 6×6 'Tesouro' + PERGAMINHO","meta":"★★☆ • 20/h • R$2,60","desc":"Cubo acetato + pérola + ferrero + mini pergaminho enrolado Mateus 6:21.","mat":"250g + acetato + ferrero","texto":"Mat. 6:21 — onde está seu tesouro","ref":"miimoska_ | Cubo com visor"},
])

slide_categoria(6, "CATEGORIA 3  —  DOCES & MIMOS QUENTINHOS: a cara do inverno lageano", "IDEIAS 10–13 • R$0,55–1,20", VERDE_CLARO, [
    {"id":"10","nome":"SOLAPA CHÁ + CHOCOLATE (campeã Lages)","meta":"★☆☆ • 45/h • R$1,20","desc":"Solapa 9×12cm sobre zip com 2 chás + choc 9g. Arte araucária + floco neve.","mat":"180g + zip 7×10 + chás","texto":"Prov. 16:24 — palavras doces","ref":"miimoska_ | Solapa Saco Transparente"},
    {"id":"11","nome":"RÓTULO PIRULITO 'Alegria'","meta":"★☆☆ • 60/h • R$0,55","desc":"Círculo 7cm com abas pop, balão + nuvem. Verso Rom.12:12. Crianças amam.","mat":"180g + pirulito pop","texto":"Rom. 12:12 — alegrem-se","ref":"miimoska_ | Rótulo Pirulito"},
    {"id":"12","nome":"PORTA BIS DUPLO 'Forte Abraço'","meta":"★☆☆ • 50/h • R$1,25","desc":"Luva para 2 bis, tons terrosos, 'Um abraço quentinho'. 6 por folha.","mat":"180g + 2 bis","texto":"1 Tes. 5:11 — encorajem-se","ref":"miimoska_ | Porta Bis"},
    {"id":"13","nome":"FAIXA GARRAFINHA 200ml 'Água da Vida'","meta":"★☆☆ • 55/h • R$0,85","desc":"Cinta 20×4cm para água mineral. Para feiras e calçadão no verão lageano.","mat":"Couchê + garrafa 200ml","texto":"João 4:14 + Apoc 22:17","ref":"miimoska_ | Cinta Garrafa/Latinha"},
])

slide_categoria(7, "CATEGORIA 4  —  PAPELARIA & UTILITÁRIOS: lembrança que dura meses", "IDEIAS 14–18 • R$0,28–3,00", RGBColor(0x9B,0x6B,0x4A), [
    {"id":"14","nome":"BLOQUINHO 7×10 'Anotações da Leitura'","meta":"★★★ • 15/h • R$1,60","desc":"Capa 300g araucária + 20fl sulfite + mini lápis. Para anotar textos bíblicos.","mat":"300g + sulfite + lápis + elástico","texto":"Salmo 119:105 — lâmpada","ref":"miimoska_ | Bloquinho + Card Lápis"},
    {"id":"15","nome":"SACHÊ PERFUMADO 'Bom Perfume'","meta":"★☆☆ • 30/h • R$1,60","desc":"Envelope 9×12cm + sachê TNT sagu lavanda ou sabonete 15g. Fica na gaveta.","mat":"Perolizado + TNT + essência","texto":"2 Cor. 2:15","ref":"miimoska_ | Envelope com Sachê"},
    {"id":"16","nome":"IMÃ GELADEIRA CALENDÁRIO + ORAÇÃO","meta":"★☆☆ • 30/h • R$0,55","desc":"7×10cm com calendário 2 meses + Pai Nosso + espaço estudo. Na geladeira 60 dias.","mat":"Fotográfico + manta + laminação","texto":"Mat. 6:9-13","ref":"miimoska_ | Imã + Calendário"},
    {"id":"17","nome":"RÉGUA MARCADOR 'Leia a Bíblia'","meta":"★☆☆ • 40/h • R$0,28","desc":"4×18cm 300g laminado, escala cm + livros da Bíblia no verso. Crianças.","mat":"Couchê 300g laminado","texto":"2 Tim. 3:16 — toda Escritura","ref":"miimoska_ | Régua Marcador"},
    {"id":"18","nome":"KIT 'Estação da Pregação' 3 em 1","meta":"★★☆ • 15/h • R$3,00","desc":"Caixa 12×9×4cm: cartão semente + chá + marcador magnético. Para revisitas.","mat":"250g + cinta + 3 recheios","texto":"Personalizável campanha","ref":"miimoska_ | Kits múltiplos (best-seller)"},
])

# SLIDE 8 - TABELA COMPARATIVA
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, BRANCO)
add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.7), fill_color=VERDE)
add_text_box(slide, Inches(0.5), Inches(0.15), Inches(8), Inches(0.4), "TABELA COMPARATIVA — CUSTO × TEMPO × IMPACTO", font_size=15, bold=True, color=BRANCO, alignment=PP_ALIGN.LEFT)
add_text_box(slide, Inches(0.5), Inches(0.35), Inches(8), Inches(0.25), "Decida em 1 minuto o que produzir com sua equipe", font_size=8, color=RGBColor(0xD9,0xE8,0xE0), alignment=PP_ALIGN.LEFT, italic=True)
# Tabela visual usando shapes: 18 linhas
# Header
header_y = Inches(0.95)
add_shape(slide, Inches(0.4), header_y, Inches(12.5), Inches(0.35), fill_color=VERDE)
headers = ["ID","IDEIA","CUSTO","TEMPO","IMPACTO","IDEAL PARA"]
widths = [0.6, 3.5, 1.8, 1.6, 1.8, 3.2]
x = Inches(0.4)
for i, h in enumerate(headers):
    w = Inches(widths[i])
    add_text_box(slide, x, header_y+Inches(0.05), w, Inches(0.25), h, font_size=7, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
    x += w

dados = [
    ("01","Cartão Semente","R$0,18","30/h","★★★ Alto","Massa • 500 casas"),
    ("02","Marcador Magnético","R$0,35","40/h","★★★ Alto","Geladeira • dura meses"),
    ("03","Card + Bala + QR","R$0,40","50/h","★★☆ Médio","Comércio centro"),
    ("04","Envelope Carta","R$0,55","25/h","★★★ Alto","Leitura em casa"),
    ("05","Caixinha Milk","R$3,00","20/h","★★★ Alto","Visita especial"),
    ("06","Sushi Brownie","R$3,00","18/h","★★★ Alto","Presente gourmet"),
    ("07","Casinha Chá","R$1,50","22/h","★★☆ Médio","Famílias • lar"),
    ("08","Travesseirinho","R$1,25","35/h","★★☆ Médio","Inverno • frio"),
    ("09","Cubo Tesouro","R$2,60","20/h","★★☆ Médio","Criança/jovem"),
    ("10","Solapa Chá+Choc","R$1,20","45/h","★★★ Alto","Revisita • quentinho"),
    ("11","Rótulo Pirulito","R$0,55","60/h","★☆☆ Rápido","Crianças • praça"),
    ("12","Porta Bis Duplo","R$1,25","50/h","★★☆ Médio","Agradecer"),
    ("13","Tag Garrafinha","R$0,85","55/h","★★☆ Médio","Eventos/feira"),
    ("14","Bloquinho","R$1,60","15/h","★★★ Alto","Estudantes"),
    ("15","Sachê Perfumado","R$1,60","30/h","★★☆ Médio","Lembrança gaveta"),
    ("16","Imã Calendário","R$0,55","30/h","★★★ Alto","Geladeira 60 dias"),
    ("17","Régua Marcador","R$0,28","40/h","★★★ Alto","Crianças/escola"),
    ("18","Kit 3 em 1","R$3,00","15/h","★★★ Alto","Estudos • premium"),
]
for idx, (id_, nome, custo, tempo, impacto, ideal) in enumerate(dados):
    y = Inches(1.35 + idx*0.30)
    bg_col = BEGE_BG if idx%2==0 else BRANCO
    add_shape(slide, Inches(0.4), y, Inches(12.5), Inches(0.30), fill_color=bg_col)
    vals = [id_, nome, custo, tempo, impacto, ideal]
    x = Inches(0.4)
    for i, val in enumerate(vals):
        w = Inches(widths[i])
        col = VERDE if i==0 else (PESSEGO if i==4 and "★★★" in val else CINZA)
        bold = (i==0 or i==1)
        add_text_box(slide, x, y+Inches(0.06), w, Inches(0.2), val, font_size=6.5, bold=bold, color=col, alignment=PP_ALIGN.CENTER if i!=1 else PP_ALIGN.LEFT)
        x += w

add_text_box(slide, Inches(0.4), Inches(6.95), Inches(12.5), Inches(0.2), "*Impacto = tempo que o morador guarda + chance de gerar conversa. Base em relatos de campanhas.", font_size=6, color=CINZA_CLARO, alignment=PP_ALIGN.CENTER, italic=True)

# SLIDE 9 - PLANO AÇÃO LAGES
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, BEGE_BG)
add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.7), fill_color=VERDE)
add_text_box(slide, Inches(0.5), Inches(0.15), Inches(9), Inches(0.4), "PLANO DE AÇÃO — LAGES EM 4 SEMANAS", font_size=16, bold=True, color=BRANCO, alignment=PP_ALIGN.LEFT)
add_text_box(slide, Inches(10.5), Inches(0.2), Inches(2.3), Inches(0.3), "❄️ 5–15°C  •  ARAUCÁRIAS", font_size=8, bold=True, color=BRANCO, alignment=PP_ALIGN.RIGHT)

# 3 colunas semanas
semanas = [
    (VERDE, "SEMANA 1", "AQUECER", "Casas 08–11h (frio ainda)\n4 pessoas → 500 cartõezinhos\nID 01 + 02\nCusto R$90", "Frase: 'Uma mensagem\nque aquece o coração'"),
    (PESSEGO, "SEMANA 2", "CENTRO", "Calçadão + praças\n300 unidades\nID 03 + 11 + 13\nCusto R$180", "QR leva ao vídeo\nda campanha • alta\nrotatividade comércio"),
    (VERDE_CLARO, "SEMANA 3–4", "REVISITAS", "Estudos marcados\n80 kits\nID 10 + 14 + 18\nCusto R$160", "Chá + bloquinho\ncriam vínculo —\nvolte em 48h"),
]
for i, (col, sem, tit, desc, frase) in enumerate(semanas):
    left = Inches(0.6 + i*4.3)
    shape = add_shape(slide, left, Inches(1.05), Inches(3.8), Inches(4.2), fill_color=BRANCO, line_color=col, radius=True)
    # header inside
    hdr = add_shape(slide, left, Inches(1.05), Inches(3.8), Inches(0.65), fill_color=col, radius=True)
    add_text_box(slide, left, Inches(1.15), Inches(3.8), Inches(0.25), sem, font_size=7, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, left, Inches(1.38), Inches(3.8), Inches(0.25), tit, font_size=10, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, left+Inches(0.3), Inches(1.95), Inches(3.2), Inches(1.2), desc, font_size=8, color=CINZA, alignment=PP_ALIGN.CENTER)
    # frase
    add_shape(slide, left+Inches(0.3), Inches(3.35), Inches(3.2), Inches(0.55), fill_color=BEGE_BG, radius=True)
    add_text_box(slide, left+Inches(0.3), Inches(3.42), Inches(3.2), Inches(0.45), frase, font_size=6.5, color=RGBColor(0x9B,0x6B,0x4A), alignment=PP_ALIGN.CENTER, italic=True)
    # icon circle
    circ = slide.shapes.add_shape(MSO_SHAPE.OVAL, left+Inches(1.5), Inches(4.05), Inches(0.8), Inches(0.8))
    circ.fill.solid(); circ.fill.fore_color.rgb = col
    circ.line.fill.background()
    icons = ["🏠","🏬","📖"]
    add_text_box(slide, left+Inches(1.5), Inches(4.2), Inches(0.8), Inches(0.5), icons[i], font_size=14, color=BRANCO, alignment=PP_ALIGN.CENTER)

# estoque
shape = add_shape(slide, Inches(0.6), Inches(5.6), Inches(12.1), Inches(0.75), fill_color=RGBColor(0x2A,0x5E,0x4F), radius=True)
add_text_box(slide, Inches(0.8), Inches(5.72), Inches(11.7), Inches(0.2), "ESTOQUE SUGERIDO PARA 1.000 MORADIAS  •  TOTAL R$ 620–780", font_size=8, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(0.8), Inches(5.95), Inches(11.7), Inches(0.25), "600 cartões (01)  •  400 marcadores (02)  •  300 balas QR (03)  •  150 solapas chá (10)  •  80 caixinhas milk (05)  •  50 kits 3em1 (18)", font_size=7, color=BRANCO, alignment=PP_ALIGN.CENTER)

add_text_box(slide, Inches(0.5), Inches(6.6), Inches(12.33), Inches(0.3), "Enxuto (só cartões): R$120 para 500 famílias  •  Completo: R$1,40 por lar  •  Mutirão 6 pessoas = 400 unid. em 2h", font_size=7, bold=True, color=VERDE, alignment=PP_ALIGN.CENTER)

# SLIDE 10 - GUIA IMPRESSÃO
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, BRANCO)
add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.7), fill_color=VERDE)
add_text_box(slide, Inches(0.5), Inches(0.15), Inches(12), Inches(0.4), "GUIA DE IMPRESSÃO & MONTAGEM — PADRÃO MIIMOSKA_", font_size=15, bold=True, color=BRANCO, alignment=PP_ALIGN.LEFT)
# 2 colunas papéis e ferramentas
# left
shape = add_shape(slide, Inches(0.5), Inches(1.0), Inches(6.2), Inches(3.2), fill_color=RGBColor(0xF0,0xF7,0xF3), radius=True)
add_text_box(slide, Inches(0.7), Inches(1.15), Inches(5.8), Inches(0.25), "📄  PAPÉIS RECOMENDADOS", font_size=9, bold=True, color=VERDE, alignment=PP_ALIGN.LEFT)
papeis = [
    "•  Cartões/marcadores: Offset 180g ou Fotográfico 180g fosco — 9 por folha A4",
    "•  Caixinhas: Couchê 230–300g / Supremo 250g — vinco obrigatório antes da cola",
    "•  Envelopes: Kraft 120g ou Offset 120g — só frente, 6 por folha",
    "•  Tags/Solapas: Couchê 180g — guilhotina, 5–6 por folha",
    "•  Imã/Calendário: Fotográfico 230g + manta magnética + laminação fosca (Lages úmida)",
]
for i, txt in enumerate(papeis):
    add_text_box(slide, Inches(0.7), Inches(1.5 + i*0.32), Inches(5.8), Inches(0.25), txt, font_size=7, color=CINZA, alignment=PP_ALIGN.LEFT)
# right
shape = add_shape(slide, Inches(6.6), Inches(1.0), Inches(6.2), Inches(3.2), fill_color=BEGE_BG, radius=True)
add_text_box(slide, Inches(6.8), Inches(1.15), Inches(5.8), Inches(0.25), "✂️  FERRAMENTAS & ACABAMENTO", font_size=9, bold=True, color=RGBColor(0x9B,0x6B,0x4A), alignment=PP_ALIGN.LEFT)
ferrs = [
    "•  Guilhotina A4 + estilete + régua metal + base corte",
    "•  Cola bastão (cards) + dupla face 12mm (caixinhas)",
    "•  Cantoneira arredondar cantos (marcadores/réguas)",
    "•  Fita cetim nº1 (0,7cm) e nº2 (1,5cm) tons outono/terrosos",
    "•  Manta magnética rolo 30cm + laminado fosco opcional",
    "•  Impressão laser colorida ou jato pigmentada — 'alta qualidade, sem margem'",
]
for i, txt in enumerate(ferrs):
    add_text_box(slide, Inches(6.8), Inches(1.5 + i*0.30), Inches(5.8), Inches(0.25), txt, font_size=7, color=CINZA, alignment=PP_ALIGN.LEFT)

# checklist compras
shape = add_shape(slide, Inches(0.5), Inches(4.45), Inches(12.33), Inches(2.2), fill_color=RGBColor(0xFF,0xFF,0xFF), line_color=RGBColor(0xE8,0xD9,0xC0), radius=True)
add_text_box(slide, Inches(0.7), Inches(4.6), Inches(11.9), Inches(0.25), "🛒  CHECKLIST RÁPIDO PARA 500 UNIDADES  •  Onde comprar em Lages / online", font_size=8, bold=True, color=VERDE, alignment=PP_ALIGN.LEFT)
checks = [
    "Offset 180g (120 fls) → Kalunga / papelarias centro  •  Couchê 230g (80 fls) → Gráfica Duque de Caxias  •  Manta (2m rolo) → Shopee",
    "Fita cetim (3 rolos) → Armarinhos Fernando  •  Balas/Bis/Pirulitos (300un) → Atacadão  •  Chás (100 sachês) → Empório Serra",
    "Brownie/pão mel (80un) → Padaria Serra / confeiteira local  •  Zip 7×10 (200un) → Embalagens Lages  •  Arquivos → wa.me/c/557183611110",
]
for i, txt in enumerate(checks):
    add_text_box(slide, Inches(0.7), Inches(4.95 + i*0.32), Inches(11.9), Inches(0.25), "•  " + txt, font_size=6.5, color=CINZA, alignment=PP_ALIGN.LEFT)
# valores
add_text_box(slide, Inches(0.7), Inches(6.0), Inches(11.9), Inches(0.25), "Arquivos miimoska_ média R$8–15 cada (paga 1 vez, imprime ilimitado)  •  Sempre peça pelo nome exato: 'Caixa Milk', 'Porta Bis', 'Solapa' etc.", font_size=6.5, color=PESSEGO, alignment=PP_ALIGN.CENTER, italic=True)

add_text_box(slide, Inches(0.5), Inches(6.95), Inches(12.33), Inches(0.25), "Faça 5 provas em sulfite antes • Cores Lages: verde araucária #2A5E4F + terracota #E87D6B + bege #F5E6D3 + pêssego", font_size=6.5, color=CINZA_CLARO, alignment=PP_ALIGN.CENTER, italic=True)

# SLIDE 11 - TEXTOS BÍBLICOS + PRÓXIMOS PASSOS
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, BRANCO)
add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.7), fill_color=VERDE)
add_text_box(slide, Inches(0.5), Inches(0.15), Inches(12), Inches(0.4), "TEXTOS BÍBLICOS & PRÓXIMOS PASSOS", font_size=16, bold=True, color=BRANCO, alignment=PP_ALIGN.LEFT)
# left textos
shape = add_shape(slide, Inches(0.5), Inches(1.0), Inches(6.2), Inches(4.8), fill_color=RGBColor(0xF0,0xF7,0xF3), radius=True)
add_text_box(slide, Inches(0.7), Inches(1.2), Inches(5.8), Inches(0.25), "📖  TEXTOS CURTOS PARA A CAMPANHA", font_size=9, bold=True, color=VERDE, alignment=PP_ALIGN.LEFT)
add_text_box(slide, Inches(0.7), Inches(1.55), Inches(5.8), Inches(0.2), "Cartões & marcadores:", font_size=7, bold=True, color=VERDE, alignment=PP_ALIGN.LEFT)
vers1 = ["Isaías 55:11 — palavra não volta vazia","Salmo 1:2 — prazer na lei de Jeová","Apoc. 21:4 — sem dor nem lágrimas","João 17:3 — conhecer a Deus"]
for i, v in enumerate(vers1):
    add_text_box(slide, Inches(0.8), Inches(1.8 + i*0.28), Inches(5.6), Inches(0.22), "•  " + v, font_size=7, color=CINZA, alignment=PP_ALIGN.LEFT)
add_text_box(slide, Inches(0.7), Inches(2.95), Inches(5.8), Inches(0.2), "Mimos quentinhos & caixinhas:", font_size=7, bold=True, color=RGBColor(0x9B,0x6B,0x4A), alignment=PP_ALIGN.LEFT)
vers2 = ["Prov. 16:24 — palavras como mel","Isaías 41:10 — não tenha medo","Salmo 34:8 — provem e vejam","Rom. 12:12 — alegrem-se na esperança"]
for i, v in enumerate(vers2):
    add_text_box(slide, Inches(0.8), Inches(3.2 + i*0.28), Inches(5.6), Inches(0.22), "•  " + v, font_size=7, color=CINZA, alignment=PP_ALIGN.LEFT)
add_shape(slide, Inches(0.7), Inches(4.5), Inches(5.8), Inches(0.6), fill_color=BEGE_BG, radius=True)
add_text_box(slide, Inches(0.8), Inches(4.6), Inches(5.6), Inches(0.45), "Dica: no verso deixe 'Anote aqui sua pergunta bíblica favorita: ___'\n— cria ponte para revisita sem pressão.", font_size=6.5, color=RGBColor(0x7A,0x6A,0x4F), alignment=PP_ALIGN.CENTER, italic=True)

# right passos
shape = add_shape(slide, Inches(6.6), Inches(1.0), Inches(6.2), Inches(4.8), fill_color=BEGE_BG, radius=True)
add_text_box(slide, Inches(6.8), Inches(1.2), Inches(5.8), Inches(0.25), "✅  PRÓXIMOS PASSOS (esta semana)", font_size=9, bold=True, color=RGBColor(0x9B,0x6B,0x4A), alignment=PP_ALIGN.LEFT)
passos = [
    "1. Escolha 3 ideias: 1 barata + 1 caixinha + 1 quentinha\n     Ex.: 01 (R$0,18) + 05 (R$3,00) + 10 (R$1,20)",
    "2. Peça arquivos à @miimoska_\n     wa.me/c/557183611110 • t.me/miimoska\n     Informe nome exato ('Caixa Milk', 'Solapa'...)",
    "3. Faça 5 provas em casa, ajuste cores outono",
    "4. Marque mutirão 2h pós-reunião — 6 pessoas = 400 unid.",
    "5. Entregue com frase: 'Um mimo quentinho\ne uma mensagem que aquece o coração —\npodemos voltar 5 minutinhos?'",
]
for i, ptxt in enumerate(passos):
    y = Inches(1.55 + i*0.62)
    # number circle
    circ = slide.shapes.add_shape(MSO_SHAPE.OVAL, Inches(6.9), y, Inches(0.3), Inches(0.3))
    circ.fill.solid(); circ.fill.fore_color.rgb = VERDE if i in [0,4] else PESSEGO
    circ.line.fill.background()
    add_text_box(slide, Inches(6.9), y+Inches(0.04), Inches(0.3), Inches(0.22), str(i+1), font_size=7, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, Inches(7.3), y, Inches(5.3), Inches(0.5), ptxt, font_size=6.8, color=CINZA, alignment=PP_ALIGN.LEFT)

# footer agradecimento
shape = add_shape(slide, Inches(0.5), Inches(6.15), Inches(12.33), Inches(0.9), fill_color=VERDE, radius=True)
add_text_box(slide, Inches(0.7), Inches(6.3), Inches(11.9), Inches(0.3), "Obrigado por levar esperança a Lages — com capricho, carinho e criatividade!  🌵✂️💛", font_size=10, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER, italic=True)
add_text_box(slide, Inches(0.7), Inches(6.65), Inches(11.9), Inches(0.2), "Inspirado no trabalho da Karine França @miimoska_ — 'Imprima, monte e venda' • Produção física liberada  •  Que Jeová abençoe cada lar alcançado", font_size=6.5, color=RGBColor(0xD9,0xE8,0xE0), alignment=PP_ALIGN.CENTER)

# SLIDE 12 - CONTATOS / CAPA FINAL
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, VERDE)
# big quote
add_text_box(slide, Inches(1.0), Inches(1.5), Inches(11.33), Inches(0.8), "\"Que seu lar seja abençoado\"", font_size=32, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER, italic=True, font_name="Calibri Light")
add_text_box(slide, Inches(1.0), Inches(2.35), Inches(11.33), Inches(0.3), "— texto para todos os mimos •  Lages, Serra Catarinense", font_size=10, color=RGBColor(0xD9,0xE8,0xE0), alignment=PP_ALIGN.CENTER)
# contatos box
shape = add_shape(slide, Inches(3.3), Inches(3.2), Inches(6.7), Inches(2.3), fill_color=BRANCO, radius=True)
add_text_box(slide, Inches(3.5), Inches(3.45), Inches(6.3), Inches(0.25), "FALE COM A @MIIMOSKA_", font_size=9, bold=True, color=VERDE, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(3.5), Inches(3.8), Inches(6.3), Inches(1.2),
             "Instagram:  instagram.com/miimoska_\nCatálogo:  wa.me/c/557183611110  (WhatsApp 71 98361-1110)\nTelegram:  t.me/miimoska  •  Grupo WhatsApp no perfil\n\nKarine França  •  Arquivos teocráticos  •  277 posts", font_size=8, color=CINZA, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(3.5), Inches(5.15), Inches(6.3), Inches(0.2), "Peça pelo nome exato do arquivo e informe 'Campanha Lages'", font_size=6.5, color=PESSEGO, alignment=PP_ALIGN.CENTER, italic=True)

add_text_box(slide, Inches(0.5), Inches(6.0), Inches(12.33), Inches(0.3), "Material de apoio — não para revenda digital  •  Arquivos originais protegidos por direitos autorais  •  Produção física liberada pela artista", font_size=6, color=RGBColor(0xD9,0xE8,0xE0), alignment=PP_ALIGN.CENTER, italic=True)
add_text_box(slide, Inches(0.5), Inches(6.4), Inches(12.33), Inches(0.25), "Preparado com carinho • Agosto 2026 • Para a campanha de pregação em Lages", font_size=7, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)
# small logo
add_text_box(slide, Inches(5.9), Inches(6.85), Inches(1.5), Inches(0.2), "✂  miimoska_", font_size=8, bold=True, color=BRANCO, alignment=PP_ALIGN.CENTER)

prs.save(PPT_PATH)
print(f"PPT salvo em {PPT_PATH}")

