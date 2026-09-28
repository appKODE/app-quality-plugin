package sample

interface FlowCollector<T> {
  suspend fun emit(value: T)
}

class Flow<T>

fun <T> flowOf(value: T): Flow<T> = Flow()
fun <T> Flow<T>.onStart(action: suspend FlowCollector<T>.() -> Unit): Flow<T> = this

fun flows() {
  flowOf(1).onStart { emit(0) }
  flowOf(1).onStart { println() }
  flowOf(1).onStart { emit(0) }.let { flowOf(2).onStart { emit(1) } }.onStart { emit(2) }
}
