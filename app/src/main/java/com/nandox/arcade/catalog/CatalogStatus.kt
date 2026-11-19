package com.nandox.arcade.catalog

enum class CatalogStatus { CATALOGED, ACQUIRED, IN_LIBRARY, UNAVAILABLE, NO_EMULATOR }

fun catalogStatus(
    acquired: Boolean,
    inLibrary: Boolean,
    sourceAvailable: Boolean,
    hasEmulator: Boolean,
): CatalogStatus = when {
    inLibrary && !sourceAvailable -> CatalogStatus.UNAVAILABLE
    inLibrary && !hasEmulator -> CatalogStatus.NO_EMULATOR
    inLibrary -> CatalogStatus.IN_LIBRARY
    acquired -> CatalogStatus.ACQUIRED
    else -> CatalogStatus.CATALOGED
}
