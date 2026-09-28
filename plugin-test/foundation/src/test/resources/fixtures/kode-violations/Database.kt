package sample

import app.cash.sqldelight.Transacter

suspend fun <T> withContext(context: Any, block: suspend () -> T): T = block()

class UserRepository(private val db: Transacter) {
  suspend fun load() {
    db.transaction {}
  }

  suspend fun loadOnIo() {
    withContext(Unit) {
      db.transaction {}
    }
  }

  fun loadBlocking() {
    db.transaction {}
  }
}

class FixedSince140(private val db: Transacter) {
  suspend fun nested() {
    suspend fun inner() { db.transaction {} }
  }

  suspend fun localInContext() {
    withContext(Unit) {
      fun inner() { db.transaction {} }
    }
  }

  suspend fun anyMembers() {
    db.toString()
    DbImpl()
  }
}

class DbImpl : Transacter
