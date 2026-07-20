package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatMessageTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    
    val now = Calendar.getInstance()
    
    return if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    } else if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) == 1) {
        "Yesterday"
    } else if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) < 7 &&
        now.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) > 0) {
        SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
