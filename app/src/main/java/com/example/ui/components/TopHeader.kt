package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VybeBorder
import com.example.R
import com.example.ui.theme.VybeTextPrimary

import androidx.compose.foundation.layout.statusBarsPadding
import com.example.ui.theme.OutfitFontFamily

@Composable
fun TopHeader(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo & App Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.vybe_logo),
                contentDescription = "VYBE logo",
                modifier = Modifier.size(34.dp)
            )

            Text(
                text = "VYBE",
                color = VybeTextPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = OutfitFontFamily,
                letterSpacing = 1.2.sp
            )
        }

        // Share Icon
        IconButton(
            onClick = { shareVybeApp(context) },
            modifier = Modifier
                .size(40.dp)
                .background(Color(0x22FFFFFF), CircleShape)
                .testTag("share_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share VYBE",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun shareVybeApp(context: Context) {
    val shareMessage = """
        🎧 I'm listening to music on VYBE — your personal music experience.

        Listen to music ad-free, discover songs made for your vibe, and enjoy your music your way.

        Download VYBE:
        https://t.me/VyomaOfficial

        🎵 VYBE — Made For Your Vibe.
    """.trimIndent()

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareMessage)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share VYBE via")
    context.startActivity(shareIntent)
}
