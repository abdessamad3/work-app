package com.coffer.app.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.coffer.app.MainActivity
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.PaymentRepository
import com.coffer.app.domain.OrderStatus
import com.coffer.app.domain.computeOrder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverdueCheckEntryPoint {
    fun orderRepository(): OrderRepository
    fun paymentRepository(): PaymentRepository
}

const val OVERDUE_NOTIFICATION_CHANNEL_ID = "overdue_payments"
const val OVERDUE_WORK_NAME = "overdue-payment-check"
private const val OVERDUE_NOTIFICATION_ID = 1001

class OverdueCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, OverdueCheckEntryPoint::class.java)
        val orders = entryPoint.orderRepository().getAllOrders().first()
        val payments = entryPoint.paymentRepository().getAllPayments().first()
        val now = System.currentTimeMillis()

        val overdueCount = orders.count { order ->
            val dueDate = order.dueDate
            dueDate != null && dueDate < now && computeOrder(order, payments).status != OrderStatus.PAID
        }

        if (overdueCount > 0) {
            postNotification(overdueCount)
        }

        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(overdueCount: Int) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        val openAppIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val orderWord = if (overdueCount == 1) "order is" else "orders are"
        val notification = NotificationCompat.Builder(applicationContext, OVERDUE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Payment overdue")
            .setContentText("$overdueCount $orderWord past its due date and not fully paid.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(OVERDUE_NOTIFICATION_ID, notification)
    }
}
