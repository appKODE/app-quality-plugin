package sample

data class Person(val name: String, val age: Int)

fun describe(person: Person): String {
  val (name, age) = person
  return person.component1() + name + age
}

fun describeAll(people: List<Person>): String =
  people.first().component1().let { people.last().component1() }
