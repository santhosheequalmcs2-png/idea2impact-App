package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.PurpleGlow
import com.example.util.StartupSoundPlayer
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class WebVec3(val x: Float, val y: Float, val z: Float)

private fun rotate3D(v: WebVec3, yaw: Float, pitch: Float): WebVec3 {
    val cosY = cos(yaw)
    val sinY = sin(yaw)
    val x1 = v.x * cosY + v.z * sinY
    val z1 = -v.x * sinY + v.z * cosY
    val cosX = cos(pitch)
    val sinX = sin(pitch)
    val y2 = v.y * cosX - z1 * sinX
    val z2 = v.y * sinX + z1 * cosX
    return WebVec3(x1, y2, z2)
}

private fun project3D(
    v: WebVec3,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    unit: Float
): Pair<Offset, Float> {
    val r = rotate3D(v, yaw, pitch)
    val perspective = 8.0f / (8.0f - r.z * 0.6f).coerceAtLeast(2.4f)
    return Offset(cx + r.x * unit * perspective, cy - r.y * unit * perspective) to r.z
}

private fun DrawScope.drawOrbitalRing(
    radius: Float,
    tiltX: Float,
    tiltZ: Float,
    frontHalf: Boolean,
    color: Color,
    strokeWidth: Float,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    unit: Float,
    dotPhase: Float? = null,
    dotColor: Color = ElectricCyan
) {
    val steps = 48
    val cosTx = cos(tiltX)
    val sinTx = sin(tiltX)
    val cosTz = cos(tiltZ)
    val sinTz = sin(tiltZ)

    fun ringVec(theta: Float): WebVec3 {
        val x0 = radius * cos(theta)
        val z0 = radius * sin(theta)
        // Tilt around X then Z
        val y1 = -z0 * sinTx
        val z1 = z0 * cosTx
        val x2 = x0 * cosTz - y1 * sinTz
        val y2 = x0 * sinTz + y1 * cosTz
        return WebVec3(x2, y2, z1)
    }

    for (i in 0 until steps) {
        val t1 = (i.toFloat() / steps) * (2f * PI.toFloat())
        val t2 = ((i + 1).toFloat() / steps) * (2f * PI.toFloat())
        val (p1, z1) = project3D(ringVec(t1), yaw, pitch, cx, cy, unit)
        val (p2, z2) = project3D(ringVec(t2), yaw, pitch, cx, cy, unit)
        val avgZ = (z1 + z2) * 0.5f
        val isFront = avgZ >= 0f
        if (isFront == frontHalf) {
            drawLine(
                color = color.copy(alpha = if (isFront) 0.92f else 0.35f),
                start = p1,
                end = p2,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }

    if (dotPhase != null) {
        val (dp, dz) = project3D(ringVec(dotPhase), yaw, pitch, cx, cy, unit)
        if ((dz >= 0f) == frontHalf) {
            drawCircle(dotColor.copy(alpha = 0.3f), radius = strokeWidth * 2.6f, center = dp)
            drawCircle(dotColor, radius = strokeWidth * 1.45f, center = dp)
        }
    }
}

/**
 * Clean, airy website-faithful 3D stage matching IDEA2IMPACT's light lavender / dark cosmic theme.
 */
@Composable
private fun WebsiteStyle3DStage(
    badgeText: String,
    accent: Color,
    testTag: String,
    height: Dp = 188.dp,
    spinSpeed: Float = 0.4f,
    initialYaw: Float = 0.35f,
    initialPitch: Float = 0.2f,
    autoOscillateYaw: Boolean = false,
    transparentStage: Boolean = false,
    showOverlayPills: Boolean = true,
    onTapObject: () -> Unit,
    modifier: Modifier = Modifier,
    drawScene: DrawScope.(
        yaw: Float,
        pitch: Float,
        phase: Float,
        pulse: Float,
        cx: Float,
        cy: Float,
        unit: Float,
        onSurfaceColor: Color,
        surfaceColor: Color
    ) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var yaw by remember { mutableFloatStateOf(initialYaw) }
    var pitch by remember { mutableFloatStateOf(initialPitch) }
    var phase by remember { mutableFloatStateOf(0f) }
    val pulseAnim = remember { Animatable(0f) }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    LaunchedEffect(spinSpeed, autoOscillateYaw) {
        var lastNs = 0L
        while (true) {
            withFrameNanos { ns ->
                if (lastNs != 0L) {
                    val dt = ((ns - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    phase += dt * 1.5f
                    if (!autoOscillateYaw) {
                        yaw += dt * spinSpeed
                    }
                }
                lastNs = ns
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (transparentStage) Modifier
                else Modifier.background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yaw += dragAmount.x * 0.012f
                    pitch = (pitch + dragAmount.y * 0.01f).coerceIn(-0.95f, 0.95f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    StartupSoundPlayer.playTap(context)
                    scope.launch {
                        pulseAnim.snapTo(1f)
                        pulseAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    onTapObject()
                }
            }
            .testTag(testTag)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bobY = sin(phase * 1.25f) * (size.minDimension * 0.018f)
            val cx = size.width / 2f
            val cy = size.height / 2f + bobY
            val unit = size.minDimension * 0.165f
            val effectiveYaw = if (autoOscillateYaw) yaw + sin(phase * 0.7f) * 0.16f else yaw
            drawScene(
                effectiveYaw,
                pitch,
                phase,
                pulseAnim.value,
                cx,
                cy,
                unit,
                onSurfaceColor,
                surfaceColor
            )
        }

        // Minimal floating badge pill (bottom-start)
        if (showOverlayPills) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Minimal interactive hint + Reset camera button (top-end / bottom-end)
            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    StartupSoundPlayer.playTap(context)
                    yaw = initialYaw
                    pitch = initialPitch
                },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset 3D orientation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 1. HOME PAGE (matches website Screenshot 1):
 * Geodesic Wireframe Icosahedron Cage + Glossy Teal-Blue Core Sphere + Diagonal Cyan Orbital Ring
 */
@Composable
fun GlobalExchange3DGlobe(
    onHubSelected: (String) -> Unit = {},
    transparentStage: Boolean = false,
    showOverlayPills: Boolean = true,
    stageHeight: Dp = 204.dp,
    modifier: Modifier = Modifier
) {
    val hubs = remember {
        listOf(
            "San Francisco · AI & Chips",
            "Zurich · Robotics & Bio",
            "Singapore · Green Tech",
            "London · Venture Capital",
            "Tokyo · Hardware IP"
        )
    }
    var hubIndex by remember { mutableIntStateOf(0) }

    // Precompute normalized icosahedron vertices & unique edges
    val icoVerts = remember {
        val phi = (1f + sqrt(5f)) * 0.5f
        val raw = listOf(
            WebVec3(-1f, phi, 0f), WebVec3(1f, phi, 0f), WebVec3(-1f, -phi, 0f), WebVec3(1f, -phi, 0f),
            WebVec3(0f, -1f, phi), WebVec3(0f, 1f, phi), WebVec3(0f, -1f, -phi), WebVec3(0f, 1f, -phi),
            WebVec3(phi, 0f, -1f), WebVec3(phi, 0f, 1f), WebVec3(-phi, 0f, -1f), WebVec3(-phi, 0f, 1f)
        )
        raw.map { v ->
            val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
            WebVec3(v.x / len, v.y / len, v.z / len)
        }
    }
    val icoEdges = remember(icoVerts) {
        buildList {
            for (i in icoVerts.indices) {
                for (j in i + 1 until icoVerts.size) {
                    val dx = icoVerts[i].x - icoVerts[j].x
                    val dy = icoVerts[i].y - icoVerts[j].y
                    val dz = icoVerts[i].z - icoVerts[j].z
                    val d = sqrt(dx * dx + dy * dy + dz * dz)
                    if (d < 1.15f) add(i to j)
                }
            }
        }
    }

    WebsiteStyle3DStage(
        badgeText = hubs[hubIndex],
        accent = CobaltBlue,
        testTag = "home_3d_globe",
        height = stageHeight,
        spinSpeed = 0.34f,
        initialYaw = 0.4f,
        initialPitch = 0.25f,
        transparentStage = transparentStage,
        showOverlayPills = showOverlayPills,
        onTapObject = {
            hubIndex = (hubIndex + 1) % hubs.size
            onHubSelected(hubs[hubIndex])
        },
        modifier = modifier
    ) { yaw, pitch, phase, pulse, cx, cy, unit, onSurfaceColor, _ ->
        val cageScale = (2.35f + pulse * 0.24f)
        val sphereRadiusPx = unit * (1.42f + pulse * 0.14f)

        // Subtle outer dashed orbit ring
        drawCircle(
            color = PurpleGlow.copy(alpha = 0.24f),
            radius = unit * 2.95f,
            center = Offset(cx, cy),
            style = Stroke(
                width = 1.6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), phase * 18f)
            )
        )

        // Rear half of cyan orbital ring
        drawOrbitalRing(
            radius = 2.75f + pulse * 0.2f,
            tiltX = 0.42f,
            tiltZ = -0.35f,
            frontHalf = false,
            color = ElectricCyan,
            strokeWidth = 2.6f,
            yaw = yaw * 0.3f,
            pitch = pitch,
            cx = cx,
            cy = cy,
            unit = unit,
            dotPhase = phase * 1.1f,
            dotColor = CobaltBlue
        )

        // Back geodesic wireframe edges
        val projCage = icoVerts.map { v ->
            project3D(WebVec3(v.x * cageScale, v.y * cageScale, v.z * cageScale), yaw, pitch, cx, cy, unit)
        }
        val wireColor = onSurfaceColor.copy(alpha = 0.78f)
        icoEdges.forEach { (i, j) ->
            val avgZ = (projCage[i].second + projCage[j].second) * 0.5f
            if (avgZ < 0f) {
                drawLine(
                    color = wireColor.copy(alpha = 0.22f),
                    start = projCage[i].first,
                    end = projCage[j].first,
                    strokeWidth = 1.5f
                )
            }
        }

        // Central 3D Teal-Blue Glossy Sphere (matching website!)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF38BDF8),
                    Color(0xFF0E7490),
                    Color(0xFF064E63)
                ),
                center = Offset(cx - sphereRadiusPx * 0.32f, cy - sphereRadiusPx * 0.32f),
                radius = sphereRadiusPx * 1.45f
            ),
            radius = sphereRadiusPx,
            center = Offset(cx, cy)
        )
        // Specular gloss highlight on sphere
        drawCircle(
            color = Color.White.copy(alpha = 0.32f),
            radius = sphereRadiusPx * 0.26f,
            center = Offset(cx - sphereRadiusPx * 0.34f, cy - sphereRadiusPx * 0.34f)
        )

        // Front geodesic wireframe edges
        icoEdges.forEach { (i, j) ->
            val avgZ = (projCage[i].second + projCage[j].second) * 0.5f
            if (avgZ >= 0f) {
                drawLine(
                    color = wireColor.copy(alpha = 0.72f),
                    start = projCage[i].first,
                    end = projCage[j].first,
                    strokeWidth = 2.0f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Geodesic node joints
        projCage.forEach { (pt, z) ->
            if (z >= 0f) {
                drawCircle(CobaltBlue, radius = 3.6f + pulse * 1.5f, center = pt)
            }
        }

        // Front half of cyan orbital ring
        drawOrbitalRing(
            radius = 2.75f + pulse * 0.2f,
            tiltX = 0.42f,
            tiltZ = -0.35f,
            frontHalf = true,
            color = ElectricCyan,
            strokeWidth = 2.8f,
            yaw = yaw * 0.3f,
            pitch = pitch,
            cx = cx,
            cy = cy,
            unit = unit,
            dotPhase = phase * 1.1f,
            dotColor = CobaltBlue
        )
    }
}

/**
 * 2. MARKETPLACE PAGE (matches website Screenshot 4):
 * Glossy 3D Purple Lightbulb with Metallic Screw Base + Tilted Blue Orbital Ring
 */
@Composable
fun MarketplaceTesseract3D(
    selectedCategory: String,
    onCycleCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    WebsiteStyle3DStage(
        badgeText = "Category: $selectedCategory · Tap bulb",
        accent = PurpleGlow,
        testTag = "marketplace_3d_tesseract",
        height = 176.dp,
        spinSpeed = 0f,
        initialYaw = 0.15f,
        initialPitch = 0.18f,
        autoOscillateYaw = true,
        onTapObject = onCycleCategory,
        modifier = modifier
    ) { yaw, pitch, phase, pulse, cx, cy, unit, _, _ ->
        val s = unit * (1f + pulse * 0.12f)
        val centerOffset = Offset(cx + sin(yaw) * unit * 0.18f, cy - unit * 0.25f)

        // Rear half of tilted blue orbital ring
        drawOrbitalRing(
            radius = 2.55f + pulse * 0.25f,
            tiltX = 0.32f,
            tiltZ = 0.34f,
            frontHalf = false,
            color = Color(0xFF2563EB),
            strokeWidth = 3.2f,
            yaw = yaw,
            pitch = pitch,
            cx = cx,
            cy = cy + unit * 0.15f,
            unit = unit,
            dotPhase = phase * 1.4f,
            dotColor = ElectricCyan
        )

        // Soft purple bulb aura
        drawCircle(
            color = PurpleGlow.copy(alpha = 0.18f + pulse * 0.28f),
            radius = s * 2.15f,
            center = centerOffset
        )

        // 3D Glossy Lightbulb Glass Silhouette (sphere dome + tapered neck)
        val bulbR = s * 1.38f
        val bx = centerOffset.x
        val by = centerOffset.y
        val neckTopY = by + bulbR * 0.62f
        val neckBotY = by + bulbR * 1.28f
        val neckHalfW = bulbR * 0.48f

        val bulbPath = Path().apply {
            moveTo(bx - neckHalfW, neckBotY)
            cubicTo(
                bx - neckHalfW * 1.05f, neckTopY + bulbR * 0.1f,
                bx - bulbR * 1.06f, by + bulbR * 0.45f,
                bx - bulbR, by
            )
            cubicTo(
                bx - bulbR, by - bulbR * 0.92f,
                bx + bulbR, by - bulbR * 0.92f,
                bx + bulbR, by
            )
            cubicTo(
                bx + bulbR * 1.06f, by + bulbR * 0.45f,
                bx + neckHalfW * 1.05f, neckTopY + bulbR * 0.1f,
                bx + neckHalfW, neckBotY
            )
            close()
        }

        drawPath(
            path = bulbPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    if (pulse > 0.15f) Color(0xFFE9D5FF) else Color(0xFFA78BFA),
                    Color(0xFF6D28D9),
                    Color(0xFF4C1D95)
                ),
                center = Offset(bx - bulbR * 0.35f, by - bulbR * 0.35f),
                radius = bulbR * 2.1f
            )
        )

        // Specular reflection arc on upper-left of bulb
        drawCircle(
            color = Color.White.copy(alpha = 0.36f + pulse * 0.3f),
            radius = bulbR * 0.28f,
            center = Offset(bx - bulbR * 0.42f, by - bulbR * 0.42f)
        )

        // Glowing internal filament loop (brightens on tap)
        val filamentColor = if (pulse > 0.15f) Color(0xFFFEF08A) else Color(0xFFDDD6FE).copy(alpha = 0.78f)
        val filPath = Path().apply {
            moveTo(bx - neckHalfW * 0.42f, neckBotY - bulbR * 0.1f)
            lineTo(bx - bulbR * 0.25f, by + bulbR * 0.1f)
            cubicTo(
                bx - bulbR * 0.12f, by - bulbR * 0.28f,
                bx + bulbR * 0.12f, by - bulbR * 0.28f,
                bx + bulbR * 0.25f, by + bulbR * 0.1f
            )
            lineTo(bx + neckHalfW * 0.42f, neckBotY - bulbR * 0.1f)
        }
        drawPath(
            path = filPath,
            color = filamentColor,
            style = Stroke(width = 2.8f + pulse * 2f, cap = StrokeCap.Round)
        )

        // 3D Metallic Screw Base (3 ribbed rings + contact tip)
        val ringH = bulbR * 0.17f
        for (idx in 0 until 3) {
            val ry = neckBotY + idx * (ringH + 2.5f)
            val rw = neckHalfW * (1f - idx * 0.06f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF94A3B8), Color(0xFF1E293B)),
                    startX = bx - rw,
                    endX = bx + rw
                ),
                topLeft = Offset(bx - rw, ry),
                size = Size(rw * 2f, ringH),
                cornerRadius = CornerRadius(ringH / 2f, ringH / 2f)
            )
        }
        // Bottom insulator contact cap
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(bx - neckHalfW * 0.48f, neckBotY + 3f * (ringH + 2.5f)),
            size = Size(neckHalfW * 0.96f, ringH * 0.9f),
            cornerRadius = CornerRadius(ringH / 2f, ringH / 2f)
        )

        // Front half of tilted blue orbital ring
        drawOrbitalRing(
            radius = 2.55f + pulse * 0.25f,
            tiltX = 0.32f,
            tiltZ = 0.34f,
            frontHalf = true,
            color = Color(0xFF2563EB),
            strokeWidth = 3.4f,
            yaw = yaw,
            pitch = pitch,
            cx = cx,
            cy = cy + unit * 0.15f,
            unit = unit,
            dotPhase = phase * 1.4f,
            dotColor = ElectricCyan
        )
    }
}

/**
 * 3. AI COPILOT PAGE (matches website Screenshot 5):
 * Cute 3D Robot Orb Companion with Dark Visor, Glowing Cyan Eyes, Silver Ear-Pods & Diamond Antenna
 */
@Composable
fun NeuralCore3D(
    isThinking: Boolean,
    onSynapsePulse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pulseCount by remember { mutableIntStateOf(0) }

    WebsiteStyle3DStage(
        badgeText = if (isThinking) "AI Sana Active" else "AI Sana Companion · Taps: $pulseCount",
        accent = if (isThinking) EmeraldSuccess else ElectricCyan,
        testTag = "copilot_3d_neural_core",
        height = 176.dp,
        spinSpeed = 0f,
        initialYaw = -0.15f,
        initialPitch = 0.1f,
        autoOscillateYaw = true,
        onTapObject = {
            pulseCount++
            onSynapsePulse()
        },
        modifier = modifier
    ) { yaw, pitch, phase, pulse, cx, cy, unit, _, _ ->
        val s = unit * (1f + pulse * 0.1f)
        val headR = s * 1.38f
        val headCenter = Offset(cx, cy + unit * 0.08f)

        // Subtle shadow disc underneath stand
        val standY = headCenter.y + headR * 1.15f
        drawOval(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF475569), Color(0xFFE2E8F0), Color(0xFF475569))
            ),
            topLeft = Offset(cx - headR * 0.72f, standY - headR * 0.16f),
            size = Size(headR * 1.44f, headR * 0.32f)
        )

        // Rear half of thin cyan orbital ring
        drawOrbitalRing(
            radius = 2.3f + pulse * 0.22f,
            tiltX = 0.35f,
            tiltZ = -0.22f,
            frontHalf = false,
            color = Color(0xFF38BDF8),
            strokeWidth = 2.4f,
            yaw = yaw,
            pitch = pitch,
            cx = headCenter.x,
            cy = headCenter.y,
            unit = unit
        )

        // Ear-pods (left & right in 3D so drag turns head naturally!)
        val earOffsetX = cos(yaw) * headR * 0.98f
        val leftEarZ = -sin(yaw)
        val rightEarZ = sin(yaw)

        fun drawEarPod(ex: Float, ey: Float, isFront: Boolean) {
            drawOval(
                brush = Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8))),
                topLeft = Offset(ex - headR * 0.18f, ey - headR * 0.3f),
                size = Size(headR * 0.36f, headR * 0.6f),
                alpha = if (isFront) 1f else 0.7f
            )
        }

        if (leftEarZ < 0f) drawEarPod(headCenter.x - earOffsetX, headCenter.y, false)
        if (rightEarZ < 0f) drawEarPod(headCenter.x + earOffsetX, headCenter.y, false)

        // Glossy Purple Metallic Robot Head Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFA855F7),
                    Color(0xFF6D28D9),
                    Color(0xFF2E1065)
                ),
                center = Offset(headCenter.x - headR * 0.32f, headCenter.y - headR * 0.42f),
                radius = headR * 1.65f
            ),
            radius = headR,
            center = headCenter
        )

        // Silver rim collar ring around bottom of head
        drawOval(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(headCenter.x - headR * 0.56f, headCenter.y + headR * 0.85f),
            size = Size(headR * 1.12f, headR * 0.2f)
        )

        // Front dark glossy visor shifted by yaw/pitch
        val visorShiftX = sin(yaw) * headR * 0.45f
        val visorShiftY = sin(pitch) * headR * 0.25f
        val visorW = headR * 1.25f * cos(yaw * 0.4f).coerceAtLeast(0.65f)
        val visorH = headR * 0.84f
        val visorX = headCenter.x + visorShiftX - visorW / 2f
        val visorY = headCenter.y + visorShiftY - visorH * 0.42f

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1E293B), Color(0xFF090D16))
            ),
            topLeft = Offset(visorX, visorY),
            size = Size(visorW, visorH),
            cornerRadius = CornerRadius(visorH * 0.45f, visorH * 0.45f)
        )
        drawRoundRect(
            color = ElectricCyan.copy(alpha = 0.35f),
            topLeft = Offset(visorX, visorY),
            size = Size(visorW, visorH),
            cornerRadius = CornerRadius(visorH * 0.45f, visorH * 0.45f),
            style = Stroke(width = 1.6f)
        )

        // Two Glowing Cyan Eyes inside visor (blink/pulse when tapped or thinking)
        val eyeColor = if (isThinking) EmeraldSuccess else ElectricCyan
        val blinkFactor = if (pulse > 0.4f || (isThinking && sin(phase * 6f) > 0.85f)) 0.35f else 1f
        val eyeW = visorW * 0.16f
        val eyeH = visorH * 0.38f * blinkFactor
        val eyeCenterY = visorY + visorH * 0.5f
        val leftEyeX = visorX + visorW * 0.32f
        val rightEyeX = visorX + visorW * 0.68f

        listOf(leftEyeX, rightEyeX).forEach { ex ->
            drawCircle(
                color = eyeColor.copy(alpha = 0.35f),
                radius = eyeW * 1.35f,
                center = Offset(ex, eyeCenterY)
            )
            drawRoundRect(
                color = eyeColor,
                topLeft = Offset(ex - eyeW / 2f, eyeCenterY - eyeH / 2f),
                size = Size(eyeW, eyeH.coerceAtLeast(4f)),
                cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f)
            )
        }

        if (leftEarZ >= 0f) drawEarPod(headCenter.x - earOffsetX, headCenter.y, true)
        if (rightEarZ >= 0f) drawEarPod(headCenter.x + earOffsetX, headCenter.y, true)

        // Antenna stem + Floating Glowing Purple Diamond Crystal on top
        val antBaseY = headCenter.y - headR * 0.94f
        val antTipY = antBaseY - headR * 0.55f - pulse * headR * 0.16f
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(headCenter.x, antBaseY),
            end = Offset(headCenter.x, antTipY + headR * 0.18f),
            strokeWidth = 3f
        )
        val dR = headR * (0.24f + pulse * 0.08f)
        val diamondPath = Path().apply {
            moveTo(headCenter.x, antTipY - dR * 1.2f)
            lineTo(headCenter.x + dR * 0.85f, antTipY)
            lineTo(headCenter.x, antTipY + dR * 1.1f)
            lineTo(headCenter.x - dR * 0.85f, antTipY)
            close()
        }
        drawCircle(PurpleGlow.copy(alpha = 0.38f), radius = dR * 1.8f, center = Offset(headCenter.x, antTipY))
        drawPath(
            path = diamondPath,
            brush = Brush.verticalGradient(listOf(Color(0xFFE9D5FF), PurpleGlow))
        )

        // Front half of thin cyan orbital ring
        drawOrbitalRing(
            radius = 2.3f + pulse * 0.22f,
            tiltX = 0.35f,
            tiltZ = -0.22f,
            frontHalf = true,
            color = Color(0xFF38BDF8),
            strokeWidth = 2.6f,
            yaw = yaw,
            pitch = pitch,
            cx = headCenter.x,
            cy = headCenter.y,
            unit = unit
        )
    }
}

/**
 * 4. TALENTS PAGE (matches website Screenshot 3):
 * 3 Stylized 3D Builder Figures on a Circular Metallic Pedestal + Floating Wireframe Diamond Above
 */
@Composable
fun BuilderConstellation3D(
    selectedRole: String,
    onCycleRole: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 178.dp
) {
    WebsiteStyle3DStage(
        badgeText = "Role: $selectedRole · Tap cluster",
        accent = CobaltBlue,
        testTag = "talents_3d_constellation",
        height = height,
        spinSpeed = 0.38f,
        initialYaw = 0.25f,
        initialPitch = 0.32f,
        onTapObject = onCycleRole,
        modifier = modifier
    ) { yaw, pitch, phase, pulse, cx, cy, unit, _, _ ->
        val discY = -0.95f
        val (discCenter, _) = project3D(WebVec3(0f, discY, 0f), yaw, pitch, cx, cy, unit)
        val discW = unit * 4.2f
        val discH = unit * 1.22f * (0.65f + abs(sin(pitch)) * 0.55f)

        // 3D Beveled Metallic Pedestal Disc
        drawOval(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFF334155))
            ),
            topLeft = Offset(discCenter.x - discW / 2f, discCenter.y - discH / 2f + unit * 0.22f),
            size = Size(discW, discH)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF475569)),
                center = Offset(discCenter.x, discCenter.y - discH * 0.15f),
                radius = discW * 0.6f
            ),
            topLeft = Offset(discCenter.x - discW / 2f, discCenter.y - discH / 2f),
            size = Size(discW, discH)
        )
        drawOval(
            color = CobaltBlue.copy(alpha = 0.65f),
            topLeft = Offset(discCenter.x - discW / 2f, discCenter.y - discH / 2f),
            size = Size(discW, discH),
            style = Stroke(width = 2.2f)
        )

        // 3 Builder Figures depth-sorted in 3D space
        val builders = listOf(
            Triple(WebVec3(-1.05f, discY, 0.35f), Color(0xFF7C3AED), Color(0xFFC4B5FD)),
            Triple(WebVec3(0.0f, discY, -0.85f), Color(0xFF0EA5E9), Color(0xFFBAE6FD)),
            Triple(WebVec3(1.05f, discY, 0.35f), Color(0xFF2563EB), Color(0xFF93C5FD))
        ).mapIndexed { idx, (pos, darkCol, lightCol) ->
            val bob = sin(phase * 1.6f + idx * 1.2f) * 0.08f + pulse * 0.28f
            val basePt = project3D(pos, yaw, pitch, cx, cy, unit)
            val topPt = project3D(WebVec3(pos.x, pos.y + 1.38f + bob, pos.z), yaw, pitch, cx, cy, unit)
            Triple(basePt, topPt, darkCol to lightCol)
        }.sortedBy { it.first.second }

        builders.forEach { (baseProj, topProj, colors) ->
            val bx = baseProj.first.x
            val by = baseProj.first.y
            val hx = topProj.first.x
            val hy = topProj.first.y
            val bodyW = unit * 0.56f
            val headR = unit * 0.32f
            val (darkCol, lightCol) = colors

            // Soft pedestal shadow under figure
            drawOval(
                color = Color(0x440F172A),
                topLeft = Offset(bx - bodyW * 0.65f, by - unit * 0.1f),
                size = Size(bodyW * 1.3f, unit * 0.22f)
            )

            // Smooth rounded 3D cone torso
            val torsoPath = Path().apply {
                moveTo(bx - bodyW * 0.72f, by)
                cubicTo(
                    bx - bodyW * 0.68f, (by + hy) * 0.52f,
                    hx - bodyW * 0.36f, hy + headR * 0.9f,
                    hx, hy + headR * 0.75f
                )
                cubicTo(
                    hx + bodyW * 0.36f, hy + headR * 0.9f,
                    bx + bodyW * 0.68f, (by + hy) * 0.52f,
                    bx + bodyW * 0.72f, by
                )
                close()
            }
            drawPath(
                path = torsoPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(lightCol, darkCol, darkCol.copy(alpha = 0.85f)),
                    startX = bx - bodyW,
                    endX = bx + bodyW
                )
            )

            // Glossy 3D spherical head
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lightCol, darkCol),
                    center = Offset(hx - headR * 0.3f, hy - headR * 0.3f),
                    radius = headR * 1.5f
                ),
                radius = headR,
                center = Offset(hx, hy)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = headR * 0.26f,
                center = Offset(hx - headR * 0.32f, hy - headR * 0.32f)
            )
        }

        // Floating Wireframe Octahedron Diamond above the trio (matching website!)
        val diaY = 1.05f + sin(phase * 1.5f) * 0.12f + pulse * 0.25f
        val dR = 0.78f
        val diaVerts = listOf(
            WebVec3(0f, diaY + dR * 1.15f, 0f),
            WebVec3(dR, diaY, 0f),
            WebVec3(0f, diaY, dR),
            WebVec3(-dR, diaY, 0f),
            WebVec3(0f, diaY, -dR),
            WebVec3(0f, diaY - dR * 1.15f, 0f)
        ).map { project3D(it, yaw * 1.25f, pitch, cx, cy, unit) }

        val diaEdges = listOf(
            0 to 1, 0 to 2, 0 to 3, 0 to 4,
            5 to 1, 5 to 2, 5 to 3, 5 to 4,
            1 to 2, 2 to 3, 3 to 4, 4 to 1
        )
        diaEdges.forEach { (i, j) ->
            drawLine(
                color = Color(0xFF818CF8),
                start = diaVerts[i].first,
                end = diaVerts[j].first,
                strokeWidth = 2.0f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 5. DASHBOARD PAGE (matches cohesive glossy website aesthetic):
 * Sleek Metallic 3D Pedestal + 4 Glossy Live-Metric Pillars + Tilted Orbital Ring
 */
@Composable
fun CapitalPipeline3D(
    postedCount: Int,
    savedCount: Int,
    proposalsCount: Int,
    upvotesCount: Int,
    modifier: Modifier = Modifier
) {
    var boostCount by remember { mutableIntStateOf(0) }

    WebsiteStyle3DStage(
        badgeText = if (boostCount == 0) "Live Capital Pipeline · Tap to surge" else "Pipeline Surge x$boostCount",
        accent = EmeraldSuccess,
        testTag = "dashboard_3d_pipeline",
        height = 178.dp,
        spinSpeed = 0.35f,
        initialYaw = 0.42f,
        initialPitch = 0.34f,
        onTapObject = { boostCount++ },
        modifier = modifier
    ) { yaw, pitch, phase, pulse, cx, cy, unit, _, _ ->
        val baseY = -1.0f
        val (discCenter, _) = project3D(WebVec3(0f, baseY, 0f), yaw, pitch, cx, cy, unit)
        val discW = unit * 4.3f
        val discH = unit * 1.24f * (0.65f + abs(sin(pitch)) * 0.55f)

        // Rear orbital ring
        drawOrbitalRing(
            radius = 2.65f + pulse * 0.2f,
            tiltX = 0.25f,
            tiltZ = -0.18f,
            frontHalf = false,
            color = ElectricCyan,
            strokeWidth = 2.4f,
            yaw = yaw,
            pitch = pitch,
            cx = cx,
            cy = cy,
            unit = unit
        )

        // Metallic circular platform
        drawOval(
            brush = Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFF334155))),
            topLeft = Offset(discCenter.x - discW / 2f, discCenter.y - discH / 2f + unit * 0.2f),
            size = Size(discW, discH)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF475569)),
                center = Offset(discCenter.x, discCenter.y - discH * 0.15f),
                radius = discW * 0.6f
            ),
            topLeft = Offset(discCenter.x - discW / 2f, discCenter.y - discH / 2f),
            size = Size(discW, discH)
        )

        val metrics = listOf(
            (postedCount.coerceAtLeast(1)).toFloat() to (PurpleGlow to Color(0xFFC4B5FD)),
            (savedCount.coerceAtLeast(1)).toFloat() to (GoldAmber to Color(0xFFFDE68A)),
            (proposalsCount.coerceAtLeast(1)).toFloat() to (EmeraldSuccess to Color(0xFFA7F3D0)),
            (upvotesCount.coerceAtLeast(2)).toFloat() to (CobaltBlue to Color(0xFF93C5FD))
        )
        val maxVal = metrics.maxOf { it.first }.coerceAtLeast(4f)
        val coords = listOf(
            WebVec3(-1.1f, baseY, 0.4f),
            WebVec3(-0.35f, baseY, -0.7f),
            WebVec3(0.45f, baseY, -0.55f),
            WebVec3(1.1f, baseY, 0.45f)
        )

        coords.mapIndexed { idx, p ->
            val h = 0.9f + (metrics[idx].first / maxVal) * 1.55f +
                pulse * (0.4f + sin(phase + idx) * 0.2f)
            val bot = project3D(p, yaw, pitch, cx, cy, unit)
            val top = project3D(WebVec3(p.x, p.y + h, p.z), yaw, pitch, cx, cy, unit)
            Triple(bot, top, metrics[idx].second)
        }.sortedBy { it.first.second }.forEach { (botProj, topProj, colors) ->
            val (darkCol, lightCol) = colors
            val barW = unit * 0.44f
            val bx = botProj.first.x
            val by = botProj.first.y
            val tx = topProj.first.x
            val ty = topProj.first.y

            val capPath = Path().apply {
                moveTo(bx - barW / 2f, by)
                lineTo(tx - barW / 2f, ty)
                lineTo(tx + barW / 2f, ty)
                lineTo(bx + barW / 2f, by)
                close()
            }
            drawPath(
                path = capPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(lightCol, darkCol),
                    startX = bx - barW / 2f,
                    endX = bx + barW / 2f
                )
            )
            drawCircle(lightCol, radius = barW * 0.5f, center = Offset(tx, ty))
            drawCircle(Color.White.copy(alpha = 0.55f), radius = barW * 0.22f, center = Offset(tx - barW * 0.12f, ty - barW * 0.1f))
        }

        // Front orbital ring
        drawOrbitalRing(
            radius = 2.65f + pulse * 0.2f,
            tiltX = 0.25f,
            tiltZ = -0.18f,
            frontHalf = true,
            color = ElectricCyan,
            strokeWidth = 2.6f,
            yaw = yaw,
            pitch = pitch,
            cx = cx,
            cy = cy,
            unit = unit
        )
    }
}

/**
 * 6. PRICING PAGE (matches website Screenshot 2):
 * Glossy 3D Purple-Indigo Metallic Sphere Medallion with Inner Shaded Diamond Facet + Tilted Blue Orbital Ring
 */
@Composable
fun TierDiamond3D(
    modifier: Modifier = Modifier
) {
    val tierFinishes = remember {
        listOf(
            Triple("Creator Pro · Royal Indigo", Color(0xFF4F46E5), Color(0xFFA78BFA)),
            Triple("Free · Emerald Spark", Color(0xFF059669), Color(0xFF6EE7B7)),
            Triple("Company · Cobalt Shield", Color(0xFF1D4ED8), Color(0xFF93C5FD)),
            Triple("Investor Pro · Sovereign Gold", Color(0xFFB45309), Color(0xFFFDE68A))
        )
    }
    var tierIndex by remember { mutableIntStateOf(0) }
    val (tierLabel, deepTint, lightTint) = tierFinishes[tierIndex]

    WebsiteStyle3DStage(
        badgeText = "$tierLabel · Tap orb",
        accent = deepTint,
        testTag = "pricing_3d_diamond",
        height = 176.dp,
        spinSpeed = 0f,
        initialYaw = 0.18f,
        initialPitch = 0.16f,
        autoOscillateYaw = true,
        onTapObject = { tierIndex = (tierIndex + 1) % tierFinishes.size },
        modifier = modifier
    ) { yaw, pitch, phase, pulse, cx, cy, unit, _, _ ->
        val sphereR = unit * (1.58f + pulse * 0.14f)

        // Rear half of tilted blue orbital ring
        drawOrbitalRing(
            radius = 2.38f + pulse * 0.22f,
            tiltX = 0.36f,
            tiltZ = 0.42f,
            frontHalf = false,
            color = Color(0xFF2563EB),
            strokeWidth = 2.6f,
            yaw = yaw,
            pitch = pitch,
            cx = cx,
            cy = cy,
            unit = unit,
            dotPhase = phase * 1.2f,
            dotColor = ElectricCyan
        )

        // Outer metallic rim ring & 3D deep-purple/indigo sphere
        drawCircle(
            color = Color(0xFF1E1B4B),
            radius = sphereR * 1.03f,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    lightTint,
                    deepTint,
                    Color(0xFF1E1B4B)
                ),
                center = Offset(cx - sphereR * 0.32f, cy - sphereR * 0.36f),
                radius = sphereR * 1.6f
            ),
            radius = sphereR,
            center = Offset(cx, cy)
        )

        // Specular gloss spot on upper-left
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = sphereR * 0.26f,
            center = Offset(cx - sphereR * 0.38f, cy - sphereR * 0.38f)
        )

        // Centered 3D Faceted Rhombus/Diamond Emblem (tilts subtly with yaw/pitch)
        val shiftX = sin(yaw) * sphereR * 0.22f
        val shiftY = sin(pitch) * sphereR * 0.18f
        val dcx = cx + shiftX
        val dcy = cy + shiftY
        val dW = sphereR * 0.48f * cos(yaw * 0.45f).coerceAtLeast(0.65f)
        val dH = sphereR * 0.56f

        val top = Offset(dcx, dcy - dH)
        val right = Offset(dcx + dW, dcy)
        val bot = Offset(dcx, dcy + dH)
        val left = Offset(dcx - dW, dcy)
        val center = Offset(dcx, dcy)

        // 4-quadrant faceted diamond shading
        val quads = listOf(
            Triple(top, left, center) to Color.White.copy(alpha = 0.72f),
            Triple(top, right, center) to lightTint.copy(alpha = 0.85f),
            Triple(left, bot, center) to lightTint.copy(alpha = 0.55f),
            Triple(right, bot, center) to deepTint.copy(alpha = 0.75f)
        )
        quads.forEach { (pts, col) ->
            val p = Path().apply {
                moveTo(pts.first.x, pts.first.y)
                lineTo(pts.second.x, pts.second.y)
                lineTo(pts.third.x, pts.third.y)
                close()
            }
            drawPath(p, col)
        }

        // Front half of tilted blue orbital ring
        drawOrbitalRing(
            radius = 2.38f + pulse * 0.22f,
            tiltX = 0.36f,
            tiltZ = 0.42f,
            frontHalf = true,
            color = Color(0xFF2563EB),
            strokeWidth = 2.8f,
            yaw = yaw,
            pitch = pitch,
            cx = cx,
            cy = cy,
            unit = unit,
            dotPhase = phase * 1.2f,
            dotColor = ElectricCyan
        )
    }
}
