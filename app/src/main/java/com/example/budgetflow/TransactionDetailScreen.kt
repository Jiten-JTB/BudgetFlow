package com.example.budgetflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TransactionDetailScreen(
    transaction: Transaction,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Back button + title ──────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.outline_home_24), // use back arrow if available
                    contentDescription = "Back",
                    tint = Accent
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Transaction Details",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Hero card ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1B3A6B), Color(0xFF0D1F44)))
                )
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(categoryEmoji(transaction.category), fontSize = 32.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    transaction.description,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "−₹${"%.2f".format(transaction.amount)}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentRed
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Detail rows ───────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                DetailRow("Category", transaction.category)
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 12.dp))
                DetailRow("Vendor / Store", transaction.vendor)
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 12.dp))
                DetailRow("Date", formatDate(transaction.date))
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 12.dp))
                DetailRow("Transaction ID", "#${transaction.id % 100000}")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextMuted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}