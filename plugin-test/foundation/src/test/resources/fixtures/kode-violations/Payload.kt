package sample

fun onEach(event: Any, block: () -> Unit) {}
fun transitionTo(reducer: (Int, String) -> Int) {}
fun action(effect: (Int, Int, String) -> Unit) {}

fun reducers() {
  onEach(Unit) {
    transitionTo { state, payload -> state }
    action { state, previous, payload -> }
    transitionTo { state, title -> state }
  }
}

fun nestedReducers() {
  onEach(Unit) {
    onEach(Unit) {
      transitionTo { state, payload -> state }
    }
  }
}
