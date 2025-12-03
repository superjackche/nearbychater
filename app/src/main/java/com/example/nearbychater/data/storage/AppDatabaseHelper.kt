package com.example.nearbychater.data.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// 数据库名称和版本号，都是常量
// 数据库版本号很重要，每次修改表结构时要递增，这样才能触发onUpgrade
private const val DB_NAME = "nearbychater.db"
private const val DB_VERSION = 3

// AppDatabaseHelper继承SQLiteOpenHelper，这是Android提供的数据库管理类
// SQLiteOpenHelper帮我们管理数据库的创建和版本升级，不用自己处理复杂的细节
// 参数：context是上下文，DB_NAME是数据库文件名，null表示用默认的CursorFactory，DB_VERSION是版本号
class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    // init块在构造函数执行后立即执行
    // WAL (Write-Ahead Logging) 模式可以提高数据库并发性能
    // 简单理解：普通模式下读写会互相阻塞，WAL模式下可以同时读写
    init {
        setWriteAheadLoggingEnabled(true)
    }

    // onCreate在数据库第一次创建时调用，只会执行一次
    // 这里创建所有需要的表
    override fun onCreate(db: SQLiteDatabase) {
        // members表：存储聊天成员的信息
        // PRIMARY KEY表示主键，member_id是唯一标识每个成员的
        // TEXT、INTEGER是SQLite的数据类型，相对简单（没有VARCHAR这种）
        // NOT NULL表示这个字段不能为空
        // DEFAULT设置默认值，0表示离线/false
        db.execSQL(
                """
            CREATE TABLE members (
                member_id TEXT PRIMARY KEY,
                local_nickname TEXT,
                remote_nickname TEXT,
                device_model TEXT,
                is_online INTEGER NOT NULL DEFAULT 0,
                last_seen_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // conversations表：存储会话信息
        // 一个会话可能是单聊或群聊
        // is_self=1表示这是自己窗口（用于发送给自己的消息）
        // pinned=1表示置顶会话
        db.execSQL(
                """
            CREATE TABLE conversations (
                conversation_id TEXT PRIMARY KEY,
                conversation_key TEXT,
                is_self INTEGER NOT NULL DEFAULT 0,
                pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // conversation_members表：会话-成员关联表（多对多关系）
        // 一个会话可以有多个成员，一个成员可以在多个会话中
        // PRIMARY KEY (conversation_id, member_id)：复合主键，保证不会重复添加同一成员
        // FOREIGN KEY：外键约束，保证数据一致性
        // ON DELETE CASCADE：当删除会话时，自动删除关联的成员记录
        db.execSQL(
                """
            CREATE TABLE conversation_members (
                conversation_id TEXT NOT NULL,
                member_id TEXT NOT NULL,
                PRIMARY KEY (conversation_id, member_id),
                FOREIGN KEY(conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE,
                FOREIGN KEY(member_id) REFERENCES members(member_id)
            )
            """.trimIndent()
        )
        // messages表：存储聊天消息
        // attachment_*字段用于存储附件信息（比如图片）
        // attachment_data存储Base64编码的图片数据
        db.execSQL(
                """
            CREATE TABLE messages (
                message_id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                content TEXT,
                timestamp INTEGER NOT NULL,
                status TEXT NOT NULL,
                should_relay INTEGER NOT NULL DEFAULT 1,
                message_type TEXT NOT NULL DEFAULT 'TEXT',
                attachment_type TEXT,
                attachment_mime TEXT,
                attachment_data TEXT,
                FOREIGN KEY(conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        // settings表：存储应用设置的键值对
        // 用TEXT存储所有类型的值（boolean转成"true"/"false"字符串）
        db.execSQL(
                """
            CREATE TABLE settings (
                setting_key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )
        // 创建索引：加快按会话ID和时间戳查询消息的速度
        // 索引就像书的目录，可以快速定位数据，不用全表扫描
        // idx_messages_conversation是索引名称
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id, timestamp)")
    }

    // onUpgrade在数据库版本号增加时调用
    // 用于数据库结构升级，比如添加新列
    // oldVersion是用户当前的版本，newVersion是最新版本
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 从版本1升级到版本2：给conversations表添加pinned列
        // ALTER TABLE是SQL的修改表结构语句
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        }
        // 从版本2升级到版本3：给messages表添加message_type列
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE messages ADD COLUMN message_type TEXT NOT NULL DEFAULT 'TEXT'")
        }
    }
}
