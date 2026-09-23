package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.CameraCaptureResult
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MaxSurfaceElevated
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextPrimary

@Composable
fun CameraCard(
    lastCapture: CameraCaptureResult?,
    sceneAnalysisText: String?,
    isProcessing: Boolean,
    onTakeSelfie: () -> Unit,
    onTakePhoto: () -> Unit,
    onAnalyzeScene: () -> Unit,
    onSilentCaptureTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(MaxSurfaceBorder, Color(0xFF6366F1)))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF6366F1).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera & Vision",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "कैमरा, सेल्फी & दृश्य AI (Vision)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "CameraX + MediaStore + Gemini 1.5/Flash Vision",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaxPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Display Last Captured Image & Scene Analysis
            AnimatedVisibility(visible = lastCapture != null) {
                lastCapture?.let { capture ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F0B1E), RoundedCornerShape(14.dp))
                            .border(1.dp, MaxSurfaceBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = capture.bitmap.asImageBitmap(),
                                contentDescription = "Captured Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (capture.isFrontCamera) Icons.Default.CameraFront else Icons.Default.CameraRear,
                                        contentDescription = null,
                                        tint = MaxSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (capture.isFrontCamera) "फ्रंट सेल्फी (Selfie)" else "बैक फोटो (Main Camera)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                if (capture.isSavedToGallery) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "गैलरी में सेव: DCIM/Max",
                                            fontSize = 11.sp,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "अस्थायी फ्रेम (AI विज़न हेतु)",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                if (!sceneAnalysisText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🤖 AI: $sceneAnalysisText",
                                        fontSize = 12.sp,
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTakeSelfie,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.CameraFront, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("सेल्फी लो", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onTakePhoto,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.CameraRear, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("फोटो खींचो", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAnalyzeScene,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.3f).height(42.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("सामने क्या है (AI)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSilentCaptureTest,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.9f).height(42.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("साइलेंट टेस्ट", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}
