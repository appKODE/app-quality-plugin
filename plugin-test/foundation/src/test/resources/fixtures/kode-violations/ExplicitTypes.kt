package sample

class Task2<A, B>

class Box<T>(val value: T)

interface ViewIntents

class ProfileModel {
  val count = 1
  val names = Box("a")
  val handler = { id: Int -> id.toString() }
  val task = Task2<Int, String>()
  val explicit: Int = 1
  private val hidden = 2

  fun titles() = Box<String?>(null)

  fun nothing() {}
}

class ProfileIntents : ViewIntents {
  val count = 1
}
