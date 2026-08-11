# Melhorias nos Gráficos BG e IOB - Resumo das Implementações

## Data: 23/12/2025

### Objetivo
Ajustar os gráficos de BG (Blood Glucose) e IOB (Insulin On Board) para ter uma aparência moderna semelhante à imagem de referência fornecida, com as seguintes funcionalidades:

## Implementações Realizadas

### 1. ✅ Linhas de Referência Tracejadas
**Localização**: `GraphData.kt` e `OverviewFragment.kt`

- **Gráfico BG Principal**: Adicionadas linhas horizontais tracejadas em valores fixos de glicose (50, 100, 150, 200, 250, 300 mg/dL)
- **Padrão Tracejado**: 10px de linha, 10px de espaço
- **Cor**: Cinza claro com transparência (0x40808080) para não interferir com os dados
- **Gráficos Secundários (IOB, COB)**: Grid configurado com estilo tracejado usando `GridStyle.BOTH`

**Código Implementado**:
```kotlin
private fun addHorizontalDashedGridLines() {
    val gridValues = if (units == GlucoseUnit.MGDL) {
        listOf(50.0, 100.0, 150.0, 200.0, 250.0, 300.0)
    } else {
        listOf(3.0, 6.0, 9.0, 12.0, 15.0, 18.0)
    }
    
    for (value in gridValues) {
        val gridLinePoints = arrayOf(
            DataPoint(overviewData.fromTime.toDouble(), value),
            DataPoint(overviewData.endTime.toDouble(), value)
        )
        
        addSeries(LineGraphSeries(gridLinePoints).also {
            it.isDrawDataPoints = false
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                paint.color = 0x40808080.toInt()
            })
        })
    }
}
```

### 2. ✅ Aparência Moderna dos Gráficos
**Localização**: `OverviewFragment.kt` - funções `onViewCreated()` e `prepareGraphsIfNeeded()`

**Melhorias Implementadas**:
- Grid com linhas tracejadas em vez de linhas sólidas
- Remoção do destaque de linhas zero (`isHighlightZeroLines = false`)
- Estilo de grid consistente entre gráfico principal e secundários
- Configuração unificada usando `apply {}` para melhor legibilidade

**Código para Gráfico BG**:
```kotlin
binding.graphsLayout.bgGraph.gridLabelRenderer?.apply {
    gridColor = rh.gac(context, app.aaps.core.ui.R.attr.graphGrid)
    gridStyle = com.jjoe64.graphview.GridLabelRenderer.GridStyle.BOTH
    isHighlightZeroLines = false
    reloadStyles()
    labelVerticalWidth = axisWidth
}
```

**Código para Gráficos Secundários (IOB)**:
```kotlin
graph.gridLabelRenderer?.apply {
    gridColor = rh.gac(context, app.aaps.core.ui.R.attr.graphGrid)
    gridStyle = com.jjoe64.graphview.GridLabelRenderer.GridStyle.BOTH
    isHighlightZeroLines = false
    reloadStyles()
    isHorizontalLabelsVisible = false
    labelVerticalWidth = axisWidth
    numVerticalLabels = 3
}
```

### 3. ✅ Zoom Dinâmico com Movimento de Pinça (Pinch-to-Zoom)
**Localização**: `OverviewFragment.kt` - `onViewCreated()`

**Funcionalidades**:
- ✅ Substituição dos botões fixos (6h/12h/18h/24h) por zoom contínuo
- ✅ Range dinâmico de 1 a 24 horas
- ✅ Valor padrão inicial: 4 horas
- ✅ Zoom sincronizado entre gráfico BG e IOB
- ✅ Atualização automática dos botões de tempo para refletir o zoom atual

**Variáveis Adicionadas**:
```kotlin
private var scaleGestureDetector: ScaleGestureDetector? = null
private var currentTimeRangeHours = 4.0 // Começar com 4 horas por padrão
private val minTimeRangeHours = 1.0 // Mínimo de 1 hora
private val maxTimeRangeHours = 24.0 // Máximo de 24 horas
```

**Implementação do Gesto de Pinça**:
```kotlin
scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scaleFactor = detector.scaleFactor
        
        // Inverter a lógica: pinça para dentro = zoom out, pinça para fora = zoom in
        currentTimeRangeHours /= scaleFactor
        
        // Limitar entre 1 e 24 horas
        currentTimeRangeHours = currentTimeRangeHours.coerceIn(minTimeRangeHours, maxTimeRangeHours)
        
        // Atualizar o range de visualização
        val newRange = currentTimeRangeHours.toInt()
        if (newRange != overviewData.rangeToDisplay) {
            overviewData.rangeToDisplay = newRange
            sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, newRange)
            rxBus.send(EventScale(newRange))
            updateTimeRangeButtons(newRange)
        }
        
        return true
    }
})
```

**Touch Listeners**:
```kotlin
// Gráfico BG
binding.graphsLayout.bgGraph.setOnTouchListener { view, event ->
    scaleGestureDetector?.onTouchEvent(event)
    false
}

// Gráfico IOB (sincronizado)
binding.graphsLayout.iobGraph.setOnTouchListener { view, event ->
    scaleGestureDetector?.onTouchEvent(event)
    false
}
```

## Arquivos Modificados

1. **OverviewFragment.kt**
   - Adicionados imports: `ScaleGestureDetector`, `MotionEvent`
   - Adicionadas variáveis para controle de zoom
   - Modificado `onViewCreated()` para configurar grid tracejado e zoom por pinça
   - Modificado `prepareGraphsIfNeeded()` para grid tracejado nos gráficos secundários

2. **GraphData.kt**
   - Modificada função `addTargetLine()` para incluir linhas de grid tracejadas
   - Adicionada função `addHorizontalDashedGridLines()` para desenhar linhas horizontais

## Como Usar

### Zoom com Gesto de Pinça
1. **Zoom In (aproximar)**: Faça movimento de pinça para fora (afastar dedos) no gráfico
2. **Zoom Out (afastar)**: Faça movimento de pinça para dentro (aproximar dedos) no gráfico
3. **Range**: O zoom funciona continuamente entre 1 e 24 horas
4. **Sincronização**: Os gráficos BG e IOB são sincronizados - zoom em um afeta o outro

### Botões de Tempo
- Os botões 6h/12h/18h/24h ainda funcionam para seleção rápida
- O botão correspondente ao zoom atual será destacado automaticamente
- Você pode usar tanto os botões quanto o gesto de pinça

## Benefícios

1. ✅ **Visual Moderno**: Linhas tracejadas dão aparência mais profissional
2. ✅ **Flexibilidade**: Zoom contínuo permite qualquer valor entre 1-24h
3. ✅ **Usabilidade**: Gesto de pinça é intuitivo e familiar
4. ✅ **Sincronização**: BG e IOB sempre mostram o mesmo período
5. ✅ **Compatibilidade**: Botões originais ainda funcionam para acesso rápido

## Notas Técnicas

- O zoom é armazenado em SharedPreferences e persiste entre sessões
- O valor padrão inicial é 4 horas (pode ser ajustado modificando `currentTimeRangeHours`)
- As linhas de grid são desenhadas como séries separadas para máximo controle visual
- O grid tracejado usa `DashPathEffect` do Android para padrão consistente
