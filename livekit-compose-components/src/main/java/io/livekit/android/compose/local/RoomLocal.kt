package io.livekit.android.compose.local

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.room.Room
import io.livekit.android.util.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A simple handler for listening to room state changes.
 * @param states the types of states to listen to, or empty list to listen to all state changes.
 * @param passedRoom the room to use, or null to use [RoomLocal] if inside a [RoomScope].
 * @param onState the listener to be called back. Will be called with the existing state.
 */
@Composable
fun HandleRoomState(
    states: List<Room.State> = emptyList(),
    passedRoom: Room? = null,
    onState: (suspend CoroutineScope.(Room, Room.State) -> Unit)?
) {
    val room = requireRoom(passedRoom = passedRoom)

    if (onState != null) {
        LaunchedEffect(room, onState) {
            Log.e("handleRoomState", "handling $onState")
            launch {
                room::state.flow.collectLatest { currentState ->
                    if (states.isEmpty() || states.contains(currentState)) {
                        onState.invoke(this, room, currentState)
                    }
                }
            }
        }
    }
}

/**
 * @see HandleRoomState
 */
@Composable
fun HandleRoomState(
    state: Room.State? = null,
    passedRoom: Room? = null,
    onState: (suspend CoroutineScope.(Room, Room.State) -> Unit)?
) {
    val states = if (state == null) emptyList() else listOf(state)
    HandleRoomState(states, passedRoom, onState)
}

@Composable
fun rememberLiveKitRoom(
    url: String? = null,
    token: String? = null,
    audio: Boolean = false,
    video: Boolean = false,
    connect: Boolean = true,
    roomOptions: RoomOptions? = null,
    liveKitOverrides: LiveKitOverrides? = null,
    connectOptions: ConnectOptions? = null,
    onConnected: (suspend CoroutineScope.(Room) -> Unit)? = null,
    onDisconnected: (suspend CoroutineScope.(Room) -> Unit)? = null,
    onError: ((Exception?) -> Unit)? = null,
    passedRoom: Room? = null,
): Room {
    val context = LocalContext.current
    val room = remember(passedRoom) {
        passedRoom ?: LiveKit.create(
            appContext = context.applicationContext,
            options = roomOptions ?: RoomOptions(),
            overrides = liveKitOverrides ?: LiveKitOverrides(),
        )
    }

    HandleRoomState(Room.State.CONNECTED, room) { _, _ -> onConnected?.invoke(this, room) }
    HandleRoomState(Room.State.CONNECTED, room) { _, _ ->
        room.localParticipant.setMicrophoneEnabled(audio)
    }
    HandleRoomState(Room.State.CONNECTED, room) { _, _ ->
        room.localParticipant.setCameraEnabled(video)
    }
    HandleRoomState(Room.State.DISCONNECTED, room) { _, _ -> onDisconnected?.invoke(this, room) }

    LaunchedEffect(room, connect, url, token, room, connectOptions, onError) {
        if (url.isNullOrEmpty() || token.isNullOrEmpty()) {
            return@LaunchedEffect
        }

        if (connect) {
            try {
                room.connect(url, token, connectOptions ?: ConnectOptions())
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    DisposableEffect(room, connect) {
        onDispose {
            if (connect) {
                room.disconnect()
            }
        }
    }
    return room
}

@Composable
fun RoomScope(
    url: String? = null,
    token: String? = null,
    audio: Boolean = false,
    video: Boolean = false,
    connect: Boolean = true,
    roomOptions: RoomOptions? = null,
    liveKitOverrides: LiveKitOverrides? = null,
    connectOptions: ConnectOptions? = null,
    onConnected: (suspend CoroutineScope.(Room) -> Unit)? = null,
    onDisconnected: (suspend CoroutineScope.(Room) -> Unit)? = null,
    onError: ((Exception?) -> Unit)? = null,
    passedRoom: Room? = null,
    content: @Composable () -> Unit
) {
    val room = rememberLiveKitRoom(
        url = url,
        token = token,
        audio = audio,
        video = video,
        connect = connect,
        roomOptions = roomOptions,
        liveKitOverrides = liveKitOverrides,
        connectOptions = connectOptions,
        onConnected = onConnected,
        onDisconnected = onDisconnected,
        onError = onError,
        passedRoom = passedRoom
    )

    CompositionLocalProvider(
        RoomLocal provides room,
        content = content,
    )
}

@Composable
@Throws(IllegalStateException::class)
fun requireRoom(passedRoom: Room? = null): Room {
    return passedRoom ?: RoomLocal.current
}

val RoomLocal =
    compositionLocalOf<Room> { throw IllegalStateException("No Room object available. This should only be used within a RoomScope.") }