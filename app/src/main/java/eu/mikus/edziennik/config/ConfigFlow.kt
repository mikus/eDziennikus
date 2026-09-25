/*
 * Copyright (c) Mikolaj Olszewski 2026-9-25.
 */
package eu.mikus.edziennik.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * A stream of [read]'s value: primed with the current value, then re-read once per config write,
 * deduped so a write to an unrelated key costs nothing downstream.
 *
 * Pass **every** [BaseConfig] instance whose keys [read] touches. There is more than one: the global
 * `App.config` and the per-profile `App.profile.config` are distinct objects with separate value
 * maps, so a reader of both that subscribes to one silently misses half its changes.
 *
 * The priming emission is what makes this safe to put into a `combine`, which produces nothing until
 * every input has emitted. Without it a screen waits on its first config write forever.
 */
fun <T> configFlow(vararg configs: BaseConfig, read: () -> T): Flow<T> =
    configs.map { it.changes }.merge()
        .map { read() }
        .onStart { emit(read()) }
        .distinctUntilChanged()
