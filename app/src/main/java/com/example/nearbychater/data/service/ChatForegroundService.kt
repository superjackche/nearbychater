package com.example.nearbychat.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.nearbychat.MainActivity
import com.example.nearbychat.R

// ChatForegroundService是一个前台服务(Foreground Service)
// 前台服务 vs 后台服务：
// - 前台服务：必须显示通知，不会被系统随意杀死，适合音乐播放、导航等
// - 后台服务：不显示通知，容易被系统杀死以节省资源
// 我们用前台服务确保应用在后台时仍能接收消息
class ChatForegroundService : Service() {

    // onBind用于绑定服务(Bound Service)
    // 我们这里不需要绑定，直接返回null
    // 绑定服务允许Activity和Service互相通信
    // 我们这个服务只需要在后台运行，不需要绑定
    override fun onBind(intent: Intent?): IBinder? = null

    // onStartCommand在服务启动时调用
    // startService()会触发这个方法
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground把服务变成前台服务
        // 参数：通知ID，通知对象
        // 调用后会立即显示通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14 (UPSIDE_DOWN_CAKE) 及以上版本需要额外参数
            startForeground(
                NOTIFICATION_ID, 
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // START_STICKY表示服务被杀死后会自动重启
        // 这样可以保证近场通信不会中断
        return START_STICKY
    }

    // 创建通知
    // 前台服务必须显示通知，不然会崩溃
    private fun createNotification(): Notification {
        // 通知渠道ID和名称
        // Android 8.0+需要创建NotificationChannel
        val channelId = "miniwechat_service_channel"
        val channelName = "MiniWeChat Service"

        // 获取NotificationManager系统服务
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Android 8.0+需要先创建通知渠道
        // 否则通知不会显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_LOW表示低优先级，不会发出声音
            val channel =
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        // PendingIntent是一种延迟执行的Intent
        // 点击通知时会打开MainActivity
        // FLAG_IMMUTABLE表示这个PendingIntent不可变，更安全
        val pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0, // requestCode
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                )

        // 使用NotificationCompat构建通知
        // Compat后缀表示兼容旧版本Android
        return NotificationCompat.Builder(this, channelId)
                .setContentTitle("MiniWeChat is running") // 通知标题
                .setContentText("Listening for nearby devices...") // 通知内容
                .setSmallIcon(R.mipmap.ic_launcher) // 通知图标
                .setContentIntent(pendingIntent) // 点击通知的动作
                .build()
    }

    // companion object相当于Java的static
    companion object {
        // 通知ID，用于更新或取消通知
        // 每个通知需要唯一的ID
        private const val NOTIFICATION_ID = 1001
    }
}