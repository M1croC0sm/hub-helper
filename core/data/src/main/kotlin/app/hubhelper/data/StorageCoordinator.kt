package app.hubhelper.data

import kotlinx.coroutines.sync.Mutex

/** Serializes operations involving both original files and their database references. */
object StorageCoordinator { val mutex = Mutex() }
