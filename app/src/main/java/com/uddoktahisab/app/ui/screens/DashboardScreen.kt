package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.BootstrapData
import com.uddoktahisab.app.data.model.SaleRecord
import com.uddoktahisab.app.ui.components.BrandMark
import com.uddoktahisab.app.ui.components.EmptyCard
import com.uddoktahisab.app.ui.components.MetricCard
import com.uddoktahisab.app.ui.components.RecordCard
import com.uddoktahisab.app.ui.components.money
import com.uddoktahisab.app.ui.components.number

@Composable
fun DashboardScreen(data: BootstrapData) {
    val d = data.dashboard; LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BrandMark(true); Spacer(Modifier.height(18.dp)); Text(
            "আসসালামু আলাইকুম,",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ); Text(data.user.fullName, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        }; item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                "আজকের বিক্রি",
                money(d.todaySales),
                Modifier.weight(1f)
            ); MetricCard("আজকের পরিমাণ", number(d.todayQuantity), Modifier.weight(1f))
        }
    }; item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                "মাসিক বিক্রি",
                money(d.monthSales),
                Modifier.weight(1f)
            ); MetricCard("মাসিক পরিমাণ", number(d.monthQuantity), Modifier.weight(1f))
        }
    }; item {
        Text(
            "সাম্প্রতিক হিসাব",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }; if (d.recentRecords.isEmpty()) item { EmptyCard("এখনও কোনো বিক্রির হিসাব নেই") }; items(
        d.recentRecords.take(
            8
        )
    ) { RecordCard(it) }
    }
}

