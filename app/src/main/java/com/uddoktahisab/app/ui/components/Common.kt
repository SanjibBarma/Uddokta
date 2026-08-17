package com.uddoktahisab.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable fun BrandMark(compact:Boolean=false){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.size(if(compact)42.dp else 58.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.secondary)),RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.AccountBalanceWallet,null,tint=MaterialTheme.colorScheme.onPrimary)}
        Column{Text("উদ্যোক্তা হিসাব",fontWeight=FontWeight.ExtraBold,fontSize=if(compact)20.sp else 27.sp);if(!compact) Text("ব্যবসার প্রতিটি হিসাব, হাতের মুঠোয়",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}
    }
}
@Composable fun LoadingOverlay(){Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha=.18f)),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
@Composable fun MetricCard(label:String,value:String,modifier:Modifier=Modifier){Card(modifier,shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(18.dp)){Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp);Spacer(Modifier.height(7.dp));Text(value,fontWeight=FontWeight.ExtraBold,fontSize=22.sp,color=MaterialTheme.colorScheme.primary)}}}
fun money(v:Double):String="৳${NumberFormat.getNumberInstance(Locale("bn","BD")).format(v)}"
fun number(v:Double):String=NumberFormat.getNumberInstance(Locale("bn","BD")).format(v)
