package app.cash.sqldelight

interface Transacter {
  fun transaction(body: () -> Unit) {}
}
