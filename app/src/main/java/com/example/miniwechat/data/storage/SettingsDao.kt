package com.example.miniwechat.data.storage

import android.content.ContentValues
import android.content.Context
// 设置项的键名，定义为常量
// private const表示私有常量，只在这个文件内可见
// 这样可以避免拼写错误，如果键名改了只需改这里
private const val KEY_DIAGNOSTICS = "diagnostics_enabled"
private const val KEY_BACKGROUND_SERVICE = "background_service_enabled"

// SettingsDao：设置数据访问对象
// 用于读写应用的各种设置项（诊断模式、后台服务等）
class SettingsDao(context: Context) {
    // helper用于访问settings表
    private val helper = AppDatabaseHelper(context.applicationContext)

    // 读取诊断功能是否启用
    // defaultValue参数指定如果数据库中没有这个设置时返回什么值
    fun diagnosticsEnabled(defaultValue: Boolean): Boolean {
        // getValue返回String?，可能为null
        // toBooleanStrictOrNull()尝试把字符串转成Boolean
        // "true" -> true, "false" -> false, 其他 -> null
        // ?:表示如果转换结果为null，就返回defaultValue
        return getValue(KEY_DIAGNOSTICS)?.toBooleanStrictOrNull() ?: defaultValue
    }

    // 设置诊断功能是否启用
    // 把Boolean转成字符串存储到数据库
    fun setDiagnosticsEnabled(enabled: Boolean) {
        // toString()把true转成"true"，false转成"false"
        setValue(KEY_DIAGNOSTICS, enabled.toString())
    }

    // 读取后台服务是否启用
    // 逻辑和diagnosticsEnabled完全相同，只是用的KEY不同
    fun backgroundServiceEnabled(defaultValue: Boolean): Boolean {
        return getValue(KEY_BACKGROUND_SERVICE)?.toBooleanStrictOrNull() ?: defaultValue
    }

    // 设置后台服务是否启用
    fun setBackgroundServiceEnabled(enabled: Boolean) {
        setValue(KEY_BACKGROUND_SERVICE, enabled.toString())
    }

    // 根据前缀读取所有匹配的设置项
    // 比如prefix="user_"，会返回所有key以"user_"开头的设置
    // 返回Map<完整key, value>
    fun readByPrefix(prefix: String): Map<String, String> {
        return helper.readableDatabase.query(
                        "settings",
                        arrayOf("key", "value"), // 只查key和value两列
                        "key LIKE ?", // where条件：key匹配某个模式
                        arrayOf("$prefix%"), // %是SQL通配符，匹配任意字符
                        null, // groupBy
                        null, // having
                        null // orderBy
                )
                .use { cursor ->
                    // buildMap创建不可变Map
                    buildMap {
                        while (cursor.moveToNext()) {
                            // getString(0)获取第0列（key）
                            // getString(1)获取第1列（value）
                            val key = cursor.getString(0)
                            val value = cursor.getString(1)
                            // 只有key和value都不为null才加入Map
                            if (key != null && value != null) {
                                put(key, value)
                            }
                        }
                    }
                }
    }

    // 根据key读取单个设置项的值
    // 如果key不存在，返回null
    fun getValue(key: String): String? {
        return helper.readableDatabase.query(
                        "settings",
                        arrayOf("value"), // 只查value列
                        "key = ?", // where条件：精确匹配key
                        arrayOf(key), // 参数替换?
                        null,
                        null,
                        null
                )
                .use { cursor ->
                    // moveToFirst()移动到第一行
                    // 如果有结果就返回value，没有结果就返回null
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
    }

    // 设置键值对
    // 如果key已存在就更新value，不存在就插入新记录
    fun setValue(key: String, value: String) {
        helper.writableDatabase.insertWithOnConflict(
                "settings",
                null,
                ContentValues().apply {
                    put("key", key)
                    put("value", value)
                },
                // CONFLICT_REPLACE：遇到主键冲突时替换旧值
                // 这样不用分别调用insert和update
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    // 删除指定的设置项
    // 如果key不存在，delete操作不会报错，只是删除0行
    fun deleteKey(key: String) {
        helper.writableDatabase.delete("settings", "key = ?", arrayOf(key))
    }
}
