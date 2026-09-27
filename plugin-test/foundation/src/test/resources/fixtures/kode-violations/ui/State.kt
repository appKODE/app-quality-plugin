package sample.ui

annotation class Immutable

interface FlowEvent

data class ScreenState(val title: String)

@Immutable
data class StableState(val title: String)

data class ClickEvent(val id: Int) : FlowEvent

interface QualifiedEvent

data class QualifiedClick(val id: Int) : QualifiedEvent
