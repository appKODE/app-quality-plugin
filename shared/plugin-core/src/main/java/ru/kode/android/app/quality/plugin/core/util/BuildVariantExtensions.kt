package ru.kode.android.app.quality.plugin.core.util

import ru.kode.android.app.quality.plugin.core.enity.BuildVariant

fun BuildVariant.capitalizedName(): String {
    return this.name.capitalized()
}
