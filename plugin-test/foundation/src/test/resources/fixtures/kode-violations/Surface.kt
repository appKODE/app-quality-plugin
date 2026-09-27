package sample

annotation class Composable

interface Modifier {
  companion object : Modifier
}

fun Modifier.background(color: Int, shape: Int = 0): Modifier = this
fun Modifier.clickable(onClick: () -> Unit): Modifier = this
fun Modifier.clip(shape: Int): Modifier = this
fun Modifier.then(other: Modifier): Modifier = other

@Composable
fun Card(modifier: Modifier = Modifier) {
  Modifier.background(1, shape = 2).clickable {}
  modifier.clip(2).background(1, shape = 2)
  Modifier.background(1).clickable {}
}

fun cards() {
  @Composable
  fun LocalCard() {
    Modifier.then(Modifier.clip(2).background(1, shape = 2))
  }
}
