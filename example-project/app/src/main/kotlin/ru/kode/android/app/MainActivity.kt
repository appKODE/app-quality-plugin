package ru.kode.android.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
  }
}

fun submit(
  selectedId: SelectedId,
  localReviewIsExists: Boolean,
) {
  when (selectedId) {
    is SelectedId.ReviewId -> {
      println("local review is not exists")
    }

    is SelectedId.ReviewGeoId if localReviewIsExists -> {
      println("local review is exists")
    }

    else -> {
      println("local review is not exists")
    }
  }
}

sealed class SelectedId {
  object ReviewId : SelectedId()

  object ReviewGeoId : SelectedId()
}
