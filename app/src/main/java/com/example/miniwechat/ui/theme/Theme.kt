package com.example.miniwechat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// DarkColorScheme: 深色模式配色方案
// Material Design 3 中定义了语义化的颜色角色
// 每个颜色角色都有特定用途,不是随意使用的
private val DarkColorScheme =
        darkColorScheme(
                // primary: 主色调,用于关键组件(如FAB按钮、重要操作)
                primary = BubbleBlueDark,
                onPrimary = Color.White, // 主色调上的文字颜色

                // primaryContainer: 主色容器,比primary更淡
                primaryContainer = Color(0xFF143464),
                onPrimaryContainer = Color(0xFFD7E2FF),

                // secondary: 次要色调,用于不太重要的组件
                secondary = BubbleGrayDark,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFF2C2C2E),
                onSecondaryContainer = Color(0xFFDADAE0),

                // tertiary: 第三色调,用于强调元素
                tertiary = OnlineGreen, // 在线状态使用绿色
                onTertiary = Color.Black,
                tertiaryContainer = Color(0xFF275F38),
                onTertiaryContainer = Color(0xFFA7F5B5),

                // background: 应用背景色
                background = BackgroundDark,
                onBackground = Color(0xFFE3E3E8),

                // surface: 表面色(卡片、对话框等)
                surface = CardDark,
                onSurface = Color(0xFFEAEAEA),
                surfaceVariant = Color(0xFF2A2A2E),
                onSurfaceVariant = Color(0xFFB1B1B5),

                // error: 错误状态色
                error = Color(0xFFFF453A),
                onError = Color.White,
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),

                // outline: 边框/分割线颜色
                outline = Color(0xFF3F3F43),
                outlineVariant = Color(0xFF2C2C2E)
        )

// LightColorScheme: 浅色模式配色方案
// 与深色模式对应,但颜色更亮,对比度不同
private val LightColorScheme =
        lightColorScheme(
                primary = BubbleBlue,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDAE5FF),
                onPrimaryContainer = Color(0xFF001A41),
                secondary = Color(0xFF5F6368),
                onSecondary = Color.White,
                secondaryContainer = BubbleGray,
                onSecondaryContainer = Color(0xFF1C1C1E),
                tertiary = OnlineGreen,
                onTertiary = Color.Black,
                tertiaryContainer = Color(0xFFCFFAD4),
                onTertiaryContainer = Color(0xFF07210E),
                background = BackgroundLight,
                onBackground = Color(0xFF1C1C1E),
                surface = CardLight,
                onSurface = Color(0xFF1C1C1E),
                surfaceVariant = BubbleGray,
                onSurfaceVariant = Color(0xFF5C5C60),
                error = Color(0xFFB3261E),
                onError = Color.White,
                errorContainer = Color(0xFFFCD8DF),
                onErrorContainer = Color(0xFF410009),
                outline = Color(0xFFDDDDDD),
                outlineVariant = Color(0xFFCACACA)
        )

// MiniwechatTheme: 应用主题Composable
// 包裹整个应用的根Composable,提供主题配置
// @Composable表示这是一个可组合函数,用于构建UI
@Composable
fun MiniwechatTheme(
        darkTheme: Boolean = isSystemInDarkTheme(), // 是否深色模式,默认跟随系统
        // Dynamic color是Android 12+的新特性
        // 可以从壁纸提取颜色,生成个性化主题
        dynamicColor: Boolean = true,
        content: @Composable () -> Unit // 子内容,lambda函数
) {
        // 根据条件选择配色方案
        val colorScheme =
                when {
                        // 条件1: Android 12+且启用动态颜色
                        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                                val context = LocalContext.current
                                // dynamicDarkColorScheme/dynamicLightColorScheme
                                // 从系统壁纸提取颜色
                                if (darkTheme) dynamicDarkColorScheme(context)
                                else dynamicLightColorScheme(context)
                        }
                        // 条件2: 深色模式
                        darkTheme -> DarkColorScheme
                        // 条件3: 浅色模式
                        else -> LightColorScheme
                }

        // MaterialTheme: Material Design 3的主题组件
        // 提供colorScheme(颜色)、typography(字体)等配置
        // content是子内容,会继承这些主题配置
        MaterialTheme(colorScheme = colorScheme, content = content)
}
