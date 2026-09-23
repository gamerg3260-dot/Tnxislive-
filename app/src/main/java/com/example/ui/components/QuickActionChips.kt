package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MaxSurfaceElevated
import com.example.ui.theme.TextPrimary

@Composable
fun QuickActionChips(
    onChipClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = listOf(
        "🛡️ एंटी थेफ्ट टेस्ट करो (Anti-Theft)",
        "🚨 साइरन बजाओ (Emergency)",
        "🛑 साइरन बंद करो (Emergency)",
        "📸 सेल्फी लो (Front Camera)",
        "📷 फोटो खींचो (Back Camera)",
        "🔍 सामने क्या है (AI Vision)",
        "🌤 आज का मौसम कैसा है (Open-Meteo)",
        "🌡 मौसम बताओ (Open-Meteo)",
        "⏰ 1 मिनट बाद टेस्ट रिमाइंडर (AlarmManager)",
        "💊 शाम 7 बजे दवाई याद दिलाना (AlarmManager)",
        "📋 मेरे सारे रिमाइंडर्स बताओ (Room DB)",
        "❌ दवाई वाला रिमाइंडर कैंसिल करो (Room DB)",
        "📞 टेस्ट कॉल करो (सिमुलेशन)",
        "📞 उठा लो (लोकल)",
        "🔴 काट दो (लोकल)",
        "🤖 मैक्स तुम बात करो (AI)",
        "📶 वाई-फ़ाई ऑन करो (लोकल)",
        "📶 वाई-फ़ाई बंद करो (लोकल)",
        "📶 मोबाइल डेटा चालू करो (लोकल)",
        "📶 मोबाइल डेटा बंद करो (लोकल)",
        "🔵 ब्लूटूथ चालू करो (लोकल)",
        "🔵 ब्लूटूथ बंद करो (लोकल)",
        "🔦 टॉर्च चालू करो (लोकल)",
        "🔦 टॉर्च बंद करो (लोकल)",
        "✈️ एरोप्लेन मोड ऑन करो (लोकल)",
        "🔊 वॉल्यूम बढ़ाओ (लोकल)",
        "🔇 आवाज म्यूट करो (लोकल)",
        "☀️ ब्राइटनेस बढ़ाओ (लोकल)",
        "🌙 DND चालू करो (लोकल)",
        "📡 हॉटस्पॉट ऑन करो (लोकल)",
        "📍 लोकेशन चालू करो (लोकल)",
        "⚡ Instagram खोलो (लोकल)",
        "⚡ WhatsApp खोलो (लोकल)",
        "⚡ YouTube खोलो (लोकल)",
        "⚡ Settings खोलो (लोकल)",
        "📷 कैमरा खोलो (लोकल)",
        "🔍 सर्च करो Shoes (Gemini AI)",
        "⚡ Ad skip karo (Gemini AI)",
        "⏭️ यह वाला टैप करो (Gemini AI)",
        "📜 नीचे स्क्रॉल करो (Gemini AI)",
        "🏠 होम स्क्रीन पर जाओ (लोकल)"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        chips.forEachIndexed { index, label ->
            AssistChip(
                onClick = {
                    val cleaned = label
                        .substringAfter(" ")
                        .replace(" (लोकल)", "")
                        .replace(" (Gemini AI)", "")
                        .trim()
                    onChipClicked(cleaned)
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (label.contains("लोकल")) MaxPrimary else TextPrimary,
                            fontSize = 12.sp
                        )
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaxSurfaceElevated,
                    labelColor = TextPrimary
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = if (label.contains("लोकल")) MaxPrimary.copy(alpha = 0.5f) else MaxSurfaceBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("quick_action_chip_$index")
            )
        }
    }
}
