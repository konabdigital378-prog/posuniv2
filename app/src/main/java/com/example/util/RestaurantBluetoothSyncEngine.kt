package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.RestaurantStaffAccount
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.data.remote.supabase.RestaurantTableRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class BtIncomingEvent {
    data class FullBundleReceived(val bundle: RestaurantFullSyncBundle) : BtIncomingEvent()
    data class OrderUpserted(val order: RestaurantOrderRecord, val fromRole: String) : BtIncomingEvent()
    data class ConfigUpserted(
        val shopId: Long,
        val staff: List<RestaurantStaffAccount>?,
        val tables: List<RestaurantTableRecord>?,
        val menuDayIds: Set<Long>?
    ) : BtIncomingEvent()
}

/**
 * Pure Bluetooth Classic (RFCOMM Star Topology) Local Sync Engine for Konab POS Restaurants.
 * - 1 Hub device (typically Caissier / Caisse centrale) accepts multiple simultaneous Client sockets
 *   (Cuisine, Serveur, Gérant).
 * - Automatic reconnect with backoff on Client devices, deduplicated event relay, and
 *   100% offline-first provisioning via FullSyncBundle + Outbox bridge to Supabase.
 */
object RestaurantBluetoothSyncEngine {
    private const val TAG = "KonabBtRestoSync"

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(BtSyncStatus())
    val status: StateFlow<BtSyncStatus> = _status.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<BtIncomingEvent>(extraBufferCapacity = 64)
    val incomingEvents: SharedFlow<BtIncomingEvent> = _incomingEvents.asSharedFlow()

    private val _discoveredCandidates = MutableStateFlow<List<BtPeerCandidate>>(emptyList())
    val discoveredCandidates: StateFlow<List<BtPeerCandidate>> = _discoveredCandidates.asStateFlow()

    private val _isScanningDevices = MutableStateFlow(false)
    val isScanningDevices: StateFlow<Boolean> = _isScanningDevices.asStateFlow()

    private var activeJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var clientWriter: BufferedWriter? = null
    private val clientWriteLock = Any()

    private data class HubPeerSession(
        val peerId: String,
        val socket: BluetoothSocket,
        val writer: BufferedWriter,
        var peerInfo: ConnectedBtPeer
    )

    private val hubPeers = ConcurrentHashMap<String, HubPeerSession>()
    private val seenEventIds = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 240
    }

    @Volatile
    private var bundleProvider: (suspend () -> RestaurantFullSyncBundle?)? = null

    @Volatile
    private var currentShopId: Long = 0L

    @Volatile
    private var currentSyncKey: String = ""

    @Volatile
    private var currentStaff: RestaurantStaffAccount? = null

    private var discoveryReceiver: BroadcastReceiver? = null

    fun setBundleProvider(provider: suspend () -> RestaurantFullSyncBundle?) {
        bundleProvider = provider
    }

    fun updateOutboxCount(count: Int) {
        _status.value = _status.value.copy(pendingOutboxCount = count.coerceAtLeast(0))
    }

    fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            emptyArray()
        }
    }

    fun hasConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        return try {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            mgr?.adapter
        } catch (_: Exception) {
            null
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            getBluetoothAdapter(context)?.isEnabled == true
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getLocalBluetoothIdentity(context: Context): Pair<String, String> {
        if (!hasConnectPermission(context)) return "" to ""
        return try {
            val adapter = getBluetoothAdapter(context) ?: return "" to ""
            val rawName = adapter.name?.trim().orEmpty()
            val rawMac = try {
                @Suppress("DEPRECATION")
                adapter.address?.trim().orEmpty()
            } catch (_: Exception) {
                ""
            }
            val validMac = if (RestaurantTeamQrParser.isValidBluetoothMac(rawMac)) rawMac else ""
            validMac to rawName
        } catch (_: SecurityException) {
            "" to ""
        } catch (_: Exception) {
            "" to ""
        }
    }

    @SuppressLint("MissingPermission")
    fun getBondedCandidates(
        context: Context,
        preferredMac: String = "",
        preferredName: String = ""
    ): List<BtPeerCandidate> {
        if (!hasConnectPermission(context)) return emptyList()
        val adapter = getBluetoothAdapter(context) ?: return emptyList()
        return try {
            val cleanMac = preferredMac.trim()
            val cleanName = preferredName.trim()
            adapter.bondedDevices.map { d ->
                val name = d.name?.trim().orEmpty().ifBlank { "Appareil (${d.address})" }
                val matchesHub = (cleanMac.isNotBlank() && d.address.equals(cleanMac, ignoreCase = true)) ||
                    (cleanName.isNotBlank() && name.equals(cleanName, ignoreCase = true))
                BtPeerCandidate(
                    name = name,
                    address = d.address,
                    isBonded = true,
                    isSuggestedHub = matchesHub
                )
            }.sortedWith(
                compareByDescending<BtPeerCandidate> { it.isSuggestedHub }
                    .thenBy { it.name.lowercase() }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(
        context: Context,
        preferredMac: String = "",
        preferredName: String = ""
    ) {
        val bonded = getBondedCandidates(context, preferredMac, preferredName)
        _discoveredCandidates.value = bonded
        if (!hasScanPermission(context) || !hasConnectPermission(context)) return
        val adapter = getBluetoothAdapter(context) ?: return
        if (!adapter.isEnabled) return

        stopDiscovery(context)
        val appCtx = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device != null) {
                            val addr = device.address ?: return
                            val name = try {
                                device.name?.trim().orEmpty().ifBlank { "Appareil ($addr)" }
                            } catch (_: SecurityException) {
                                "Appareil ($addr)"
                            }
                            val isSuggested = (preferredMac.isNotBlank() && addr.equals(preferredMac, ignoreCase = true)) ||
                                (preferredName.isNotBlank() && name.equals(preferredName, ignoreCase = true))
                            val current = _discoveredCandidates.value.toMutableList()
                            if (current.none { it.address.equals(addr, ignoreCase = true) }) {
                                current.add(
                                    BtPeerCandidate(
                                        name = name,
                                        address = addr,
                                        isBonded = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: Exception) { false },
                                        isSuggestedHub = isSuggested
                                    )
                                )
                                _discoveredCandidates.value = current.sortedWith(
                                    compareByDescending<BtPeerCandidate> { it.isSuggestedHub }
                                        .thenByDescending { it.isBonded }
                                        .thenBy { it.name.lowercase() }
                                )
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _isScanningDevices.value = false
                    }
                }
            }
        }
        discoveryReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appCtx.registerReceiver(receiver, filter)
        }
        _isScanningDevices.value = true
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: Exception) {
            _isScanningDevices.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context) {
        _isScanningDevices.value = false
        try {
            if (hasScanPermission(context)) {
                getBluetoothAdapter(context)?.cancelDiscovery()
            }
        } catch (_: Exception) {}
        val rec = discoveryReceiver
        discoveryReceiver = null
        if (rec != null) {
            try {
                context.applicationContext.unregisterReceiver(rec)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    fun startHub(
        context: Context,
        shopId: Long,
        syncKey: String,
        staff: RestaurantStaffAccount?
    ) {
        val appCtx = context.applicationContext
        if (!hasConnectPermission(appCtx)) {
            _status.value = _status.value.copy(
                mode = RestaurantBluetoothMode.HUB,
                isRunning = false,
                statusMessage = "Permission Bluetooth (Appareils à proximité) requise."
            )
            return
        }
        val adapter = getBluetoothAdapter(appCtx)
        if (adapter == null || !adapter.isEnabled) {
            _status.value = _status.value.copy(
                mode = RestaurantBluetoothMode.HUB,
                isRunning = false,
                statusMessage = "Activez le Bluetooth sur cet appareil pour démarrer le Hub."
            )
            return
        }

        stop()
        currentShopId = shopId
        currentSyncKey = syncKey
        currentStaff = staff
        RestaurantBluetoothStorage.setMode(appCtx, RestaurantBluetoothMode.HUB)

        val (_, localName) = getLocalBluetoothIdentity(appCtx)
        _status.value = _status.value.copy(
            mode = RestaurantBluetoothMode.HUB,
            isRunning = true,
            isConnected = false,
            isConnecting = false,
            localBtName = localName,
            connectedPeers = emptyList(),
            statusMessage = "Hub Bluetooth actif • En attente des postes (Cuisine / Serveur)…"
        )

        activeJob = engineScope.launch {
            var srv: BluetoothServerSocket? = null
            try {
                srv = adapter.listenUsingRfcommWithServiceRecord(
                    RestaurantBtWireProtocol.SERVICE_NAME,
                    RestaurantBtWireProtocol.SERVICE_UUID
                )
                serverSocket = srv

                while (isActive) {
                    val incoming: BluetoothSocket = try {
                        srv.accept()
                    } catch (e: IOException) {
                        if (isActive) {
                            Log.w(TAG, "Hub accept terminé: ${e.message}")
                        }
                        break
                    }
                    launch {
                        handleHubPeerSocket(incoming)
                    }
                }
            } catch (se: SecurityException) {
                _status.value = _status.value.copy(
                    isRunning = false,
                    statusMessage = "Autorisez la permission Bluetooth pour activer le Hub."
                )
            } catch (ioe: IOException) {
                _status.value = _status.value.copy(
                    isRunning = false,
                    statusMessage = "Hub Bluetooth interrompu : ${ioe.localizedMessage ?: "erreur socket"}"
                )
            } finally {
                try {
                    srv?.close()
                } catch (_: Exception) {}
                serverSocket = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleHubPeerSocket(socket: BluetoothSocket) {
        val peerId = UUID.randomUUID().toString()
        val devName = try { socket.remoteDevice?.name ?: "Poste Équipe" } catch (_: Exception) { "Poste Équipe" }
        val devAddr = try { socket.remoteDevice?.address.orEmpty() } catch (_: Exception) { "" }

        val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

        val session = HubPeerSession(
            peerId = peerId,
            socket = socket,
            writer = writer,
            peerInfo = ConnectedBtPeer(
                peerId = peerId,
                deviceName = devName,
                deviceAddress = devAddr,
                staffName = devName,
                role = RestaurantStaffRole.WAITER
            )
        )
        hubPeers[peerId] = session
        refreshHubPeersState()

        try {
            while (engineScope.isActive) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                processHubFrame(session, line)
            }
        } catch (_: Exception) {
        } finally {
            hubPeers.remove(peerId)
            try {
                socket.close()
            } catch (_: Exception) {}
            refreshHubPeersState()
        }
    }

    private suspend fun processHubFrame(sender: HubPeerSession, rawLine: String) {
        val obj = try { JSONObject(rawLine) } catch (_: Exception) { return }
        val type = obj.optString("type", "")
        val eventId = obj.optString("event_id", "")
        val incomingKey = obj.optString("sync_key", "")
        val payload = obj.optJSONObject("payload") ?: JSONObject()

        if (currentSyncKey.isNotBlank() && incomingKey.isNotBlank() &&
            !incomingKey.equals(currentSyncKey, ignoreCase = true)
        ) {
            Log.w(TAG, "Clé sync Bluetooth invalide reçue de ${sender.peerInfo.deviceName}")
            return
        }

        if (eventId.isNotBlank()) {
            synchronized(seenEventIds) {
                if (seenEventIds.containsKey(eventId)) return
                seenEventIds[eventId] = true
            }
        }

        when (type) {
            RestaurantBtWireProtocol.TYPE_HELLO -> {
                val role = RestaurantStaffRole.fromString(payload.optString("role", "WAITER"))
                val staffName = payload.optString("staff_name", sender.peerInfo.deviceName)
                    .ifBlank { sender.peerInfo.deviceName }
                sender.peerInfo = sender.peerInfo.copy(
                    staffName = staffName,
                    role = role,
                    lastSeenAt = System.currentTimeMillis()
                )
                refreshHubPeersState()

                val ack = RestaurantBtWireProtocol.buildFrame(
                    type = RestaurantBtWireProtocol.TYPE_HELLO_ACK,
                    shopId = currentShopId,
                    syncKey = currentSyncKey,
                    payload = JSONObject().apply {
                        put("hub_staff", currentStaff?.name ?: "Caisse Hub")
                        put("sync_key", currentSyncKey)
                    }
                )
                writeLineSafe(sender.writer, ack)
                pushFullBundleToPeer(sender)
            }

            RestaurantBtWireProtocol.TYPE_FULL_SYNC_REQUEST -> {
                pushFullBundleToPeer(sender)
            }

            RestaurantBtWireProtocol.TYPE_ORDER_UPSERT -> {
                val orderJson = payload.optJSONObject("order") ?: return
                val order = RestaurantOrderRecord.fromJson(orderJson)
                val fromRole = payload.optString("from_role", sender.peerInfo.role.name)
                sender.peerInfo = sender.peerInfo.copy(lastSeenAt = System.currentTimeMillis())
                _incomingEvents.tryEmit(BtIncomingEvent.OrderUpserted(order, fromRole))
                _status.value = _status.value.copy(
                    lastSyncAt = System.currentTimeMillis(),
                    statusMessage = "Reçu de ${sender.peerInfo.staffName} : ${order.orderNumber} (${order.tableName})"
                )
                // Fan out to all other connected clients in the restaurant!
                hubPeers.values.filter { it.peerId != sender.peerId }.forEach { other ->
                    writeLineSafe(other.writer, rawLine)
                }
            }

            RestaurantBtWireProtocol.TYPE_CONFIG_UPSERT -> {
                emitConfigFromPayload(obj.optLong("shop_id", currentShopId), payload)
                hubPeers.values.filter { it.peerId != sender.peerId }.forEach { other ->
                    writeLineSafe(other.writer, rawLine)
                }
            }

            RestaurantBtWireProtocol.TYPE_PING -> {
                val pong = RestaurantBtWireProtocol.buildFrame(
                    type = RestaurantBtWireProtocol.TYPE_PONG,
                    shopId = currentShopId,
                    syncKey = currentSyncKey
                )
                writeLineSafe(sender.writer, pong)
            }
        }
    }

    private suspend fun pushFullBundleToPeer(peer: HubPeerSession) {
        val bundle = try { bundleProvider?.invoke() } catch (_: Exception) { null } ?: return
        val frame = RestaurantBtWireProtocol.buildFrame(
            type = RestaurantBtWireProtocol.TYPE_FULL_SYNC_BUNDLE,
            shopId = bundle.shopId,
            syncKey = currentSyncKey,
            payload = bundle.toJson()
        )
        writeLineSafe(peer.writer, frame)
    }

    fun pushFullBundleToAllClients() {
        if (_status.value.mode != RestaurantBluetoothMode.HUB || hubPeers.isEmpty()) return
        engineScope.launch {
            hubPeers.values.toList().forEach { peer ->
                pushFullBundleToPeer(peer)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToHub(
        context: Context,
        hubAddress: String = "",
        hubName: String = "",
        shopId: Long,
        syncKey: String,
        staff: RestaurantStaffAccount?
    ) {
        val appCtx = context.applicationContext
        if (!hasConnectPermission(appCtx)) {
            _status.value = _status.value.copy(
                mode = RestaurantBluetoothMode.CLIENT,
                isConnected = false,
                isConnecting = false,
                statusMessage = "Permission Bluetooth requise pour se connecter au poste principal."
            )
            return
        }
        val adapter = getBluetoothAdapter(appCtx)
        if (adapter == null || !adapter.isEnabled) {
            _status.value = _status.value.copy(
                mode = RestaurantBluetoothMode.CLIENT,
                isConnected = false,
                isConnecting = false,
                statusMessage = "Activez le Bluetooth pour vous connecter au Hub du restaurant."
            )
            return
        }

        val resolvedAddr = resolveTargetAddress(adapter, hubAddress, hubName)
        if (resolvedAddr.isBlank()) {
            _status.value = _status.value.copy(
                mode = RestaurantBluetoothMode.CLIENT,
                isRunning = false,
                isConnected = false,
                isConnecting = false,
                hubDeviceName = hubName,
                statusMessage = "Sélectionnez le téléphone Hub du restaurant dans la liste Bluetooth."
            )
            return
        }

        stop()
        currentShopId = shopId
        currentSyncKey = syncKey
        currentStaff = staff
        RestaurantBluetoothStorage.setMode(appCtx, RestaurantBluetoothMode.CLIENT)
        RestaurantBluetoothStorage.saveHubTarget(appCtx, resolvedAddr, hubName, syncKey)

        val (_, localName) = getLocalBluetoothIdentity(appCtx)
        val displayHubName = hubName.ifBlank { resolvedAddr }

        activeJob = engineScope.launch {
            var backoffMs = 2_500L
            while (isActive) {
                _status.value = _status.value.copy(
                    mode = RestaurantBluetoothMode.CLIENT,
                    isRunning = true,
                    isConnected = false,
                    isConnecting = true,
                    localBtName = localName,
                    hubDeviceAddress = resolvedAddr,
                    hubDeviceName = displayHubName,
                    statusMessage = "Connexion Bluetooth vers $displayHubName…"
                )

                var sock: BluetoothSocket? = null
                try {
                    try {
                        if (hasScanPermission(appCtx)) adapter.cancelDiscovery()
                    } catch (_: Exception) {}

                    val device = adapter.getRemoteDevice(resolvedAddr)
                    sock = connectRfcommWithFallback(device)
                    synchronized(clientWriteLock) {
                        clientSocket = sock
                        clientWriter = BufferedWriter(OutputStreamWriter(sock.outputStream, Charsets.UTF_8))
                    }
                    backoffMs = 2_500L

                    _status.value = _status.value.copy(
                        mode = RestaurantBluetoothMode.CLIENT,
                        isRunning = true,
                        isConnected = true,
                        isConnecting = false,
                        hubDeviceAddress = resolvedAddr,
                        hubDeviceName = device.name?.ifBlank { displayHubName } ?: displayHubName,
                        statusMessage = "Connecté au Hub Bluetooth (${device.name ?: displayHubName})"
                    )

                    // Send HELLO + Full Sync Request
                    val hello = RestaurantBtWireProtocol.buildFrame(
                        type = RestaurantBtWireProtocol.TYPE_HELLO,
                        shopId = currentShopId,
                        syncKey = currentSyncKey,
                        payload = JSONObject().apply {
                            put("staff_id", currentStaff?.id.orEmpty())
                            put("staff_name", currentStaff?.name ?: "Équipe")
                            put("role", (currentStaff?.role ?: RestaurantStaffRole.WAITER).name)
                        }
                    )
                    sendClientLine(hello)

                    val reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.UTF_8))
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        processClientFrame(line)
                    }
                } catch (se: SecurityException) {
                    _status.value = _status.value.copy(
                        isConnected = false,
                        isConnecting = false,
                        statusMessage = "Autorisation Bluetooth manquante."
                    )
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Connexion client Bluetooth perdue: ${e.message}")
                    _status.value = _status.value.copy(
                        isConnected = false,
                        isConnecting = false,
                        statusMessage = "Hub hors portée — nouvelle tentative dans ${backoffMs / 1000}s…"
                    )
                } finally {
                    synchronized(clientWriteLock) {
                        try { sock?.close() } catch (_: Exception) {}
                        clientSocket = null
                        clientWriter = null
                    }
                }

                if (!isActive) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(12_000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveTargetAddress(
        adapter: BluetoothAdapter,
        preferredMac: String,
        preferredName: String
    ): String {
        if (RestaurantTeamQrParser.isValidBluetoothMac(preferredMac)) {
            return preferredMac.trim()
        }
        val cleanName = preferredName.trim()
        if (cleanName.isNotBlank()) {
            val match = try {
                adapter.bondedDevices.firstOrNull {
                    it.name?.trim()?.equals(cleanName, ignoreCase = true) == true
                }
            } catch (_: Exception) {
                null
            }
            if (match != null) return match.address
        }
        return ""
    }

    @SuppressLint("MissingPermission")
    private fun connectRfcommWithFallback(device: BluetoothDevice): BluetoothSocket {
        val primary = device.createRfcommSocketToServiceRecord(RestaurantBtWireProtocol.SERVICE_UUID)
        return try {
            primary.connect()
            primary
        } catch (primaryErr: IOException) {
            try { primary.close() } catch (_: Exception) {}
            // Reflection channel 1 fallback for some MediaTek / POS handsets
            val fallback = device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
            fallback.connect()
            fallback
        }
    }

    private fun processClientFrame(rawLine: String) {
        val obj = try { JSONObject(rawLine) } catch (_: Exception) { return }
        val type = obj.optString("type", "")
        val eventId = obj.optString("event_id", "")
        val payload = obj.optJSONObject("payload") ?: JSONObject()

        if (eventId.isNotBlank()) {
            synchronized(seenEventIds) {
                if (seenEventIds.containsKey(eventId)) return
                seenEventIds[eventId] = true
            }
        }

        when (type) {
            RestaurantBtWireProtocol.TYPE_HELLO_ACK -> {
                val hubStaff = payload.optString("hub_staff", "Hub")
                val ackKey = payload.optString("sync_key", "").trim()
                if (ackKey.isNotBlank() && currentSyncKey.isBlank()) {
                    currentSyncKey = ackKey
                }
                _status.value = _status.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    lastSyncAt = System.currentTimeMillis(),
                    statusMessage = "Synchronisé avec $hubStaff"
                )
            }

            RestaurantBtWireProtocol.TYPE_FULL_SYNC_BUNDLE -> {
                val bundle = RestaurantFullSyncBundle.fromJson(payload)
                _incomingEvents.tryEmit(BtIncomingEvent.FullBundleReceived(bundle))
                _status.value = _status.value.copy(
                    lastSyncAt = System.currentTimeMillis(),
                    statusMessage = "Menu & tables reçus (${bundle.products.size} plats, ${bundle.tables.size} tables)"
                )
            }

            RestaurantBtWireProtocol.TYPE_ORDER_UPSERT -> {
                val orderJson = payload.optJSONObject("order") ?: return
                val order = RestaurantOrderRecord.fromJson(orderJson)
                val fromRole = payload.optString("from_role", "")
                _incomingEvents.tryEmit(BtIncomingEvent.OrderUpserted(order, fromRole))
                _status.value = _status.value.copy(
                    lastSyncAt = System.currentTimeMillis(),
                    statusMessage = "Synchro Bluetooth : ${order.orderNumber} (${order.status.shortBadge})"
                )
            }

            RestaurantBtWireProtocol.TYPE_CONFIG_UPSERT -> {
                emitConfigFromPayload(obj.optLong("shop_id", currentShopId), payload)
                _status.value = _status.value.copy(
                    lastSyncAt = System.currentTimeMillis(),
                    statusMessage = "Configuration restaurant mise à jour via Hub"
                )
            }
        }
    }

    private fun emitConfigFromPayload(shopId: Long, payload: JSONObject) {
        val staff = if (payload.has("staff")) {
            val arr = payload.optJSONArray("staff") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { RestaurantStaffAccount.fromJson(it) } }
        } else null

        val tables = if (payload.has("tables")) {
            val arr = payload.optJSONArray("tables") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { RestaurantTableRecord.fromJson(it) } }
        } else null

        val menuIds = if (payload.has("menu_day_ids")) {
            val arr = payload.optJSONArray("menu_day_ids") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i -> arr.optLong(i, 0L).takeIf { it > 0L } }.toSet()
        } else null

        _incomingEvents.tryEmit(BtIncomingEvent.ConfigUpserted(shopId, staff, tables, menuIds))
    }

    fun requestFullSyncFromHub() {
        if (_status.value.mode != RestaurantBluetoothMode.CLIENT || !_status.value.isConnected) return
        engineScope.launch {
            val req = RestaurantBtWireProtocol.buildFrame(
                type = RestaurantBtWireProtocol.TYPE_FULL_SYNC_REQUEST,
                shopId = currentShopId,
                syncKey = currentSyncKey
            )
            sendClientLine(req)
        }
    }

    fun broadcastOrder(order: RestaurantOrderRecord, fromRole: String = "") {
        val frame = RestaurantBtWireProtocol.buildFrame(
            type = RestaurantBtWireProtocol.TYPE_ORDER_UPSERT,
            shopId = order.shopId.takeIf { it > 0L } ?: currentShopId,
            syncKey = currentSyncKey,
            payload = JSONObject().apply {
                put("order", order.toJson())
                put("from_role", fromRole)
            }
        )
        broadcastRawFrame(frame)
    }

    fun broadcastConfig(
        shopId: Long,
        staff: List<RestaurantStaffAccount>? = null,
        tables: List<RestaurantTableRecord>? = null,
        menuDayIds: Set<Long>? = null
    ) {
        val payload = JSONObject().apply {
            staff?.let { list ->
                val arr = JSONArray()
                list.forEach { arr.put(it.toJson()) }
                put("staff", arr)
            }
            tables?.let { list ->
                val arr = JSONArray()
                list.forEach { arr.put(it.toJson()) }
                put("tables", arr)
            }
            menuDayIds?.let { ids ->
                val arr = JSONArray()
                ids.forEach { arr.put(it) }
                put("menu_day_ids", arr)
            }
        }
        val frame = RestaurantBtWireProtocol.buildFrame(
            type = RestaurantBtWireProtocol.TYPE_CONFIG_UPSERT,
            shopId = shopId,
            syncKey = currentSyncKey,
            payload = payload
        )
        broadcastRawFrame(frame)
    }

    private fun broadcastRawFrame(frame: String) {
        val mode = _status.value.mode
        if (mode == RestaurantBluetoothMode.STANDALONE) return
        engineScope.launch {
            when (mode) {
                RestaurantBluetoothMode.HUB -> {
                    hubPeers.values.forEach { peer ->
                        writeLineSafe(peer.writer, frame)
                    }
                }
                RestaurantBluetoothMode.CLIENT -> {
                    sendClientLine(frame)
                }
                RestaurantBluetoothMode.STANDALONE -> {}
            }
        }
    }

    private fun sendClientLine(line: String) {
        synchronized(clientWriteLock) {
            val w = clientWriter ?: return
            try {
                w.write(line)
                w.newLine()
                w.flush()
            } catch (_: Exception) {}
        }
    }

    private fun writeLineSafe(writer: BufferedWriter, line: String) {
        try {
            synchronized(writer) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        } catch (_: Exception) {}
    }

    private fun refreshHubPeersState() {
        val list = hubPeers.values.map { it.peerInfo }.sortedBy { it.role.ordinal }
        _status.value = _status.value.copy(
            connectedPeers = list,
            isConnected = list.isNotEmpty(),
            statusMessage = if (list.isEmpty()) {
                "Hub Bluetooth actif • En attente des postes…"
            } else {
                "${list.size} poste(s) connecté(s) en Bluetooth"
            }
        )
    }

    fun stop(resetSavedMode: Boolean = false, context: Context? = null) {
        activeJob?.cancel()
        activeJob = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null

        synchronized(clientWriteLock) {
            try { clientSocket?.close() } catch (_: Exception) {}
            clientSocket = null
            clientWriter = null
        }

        hubPeers.values.forEach { session ->
            try { session.socket.close() } catch (_: Exception) {}
        }
        hubPeers.clear()

        if (resetSavedMode && context != null) {
            RestaurantBluetoothStorage.setMode(context, RestaurantBluetoothMode.STANDALONE)
        }
        _status.value = _status.value.copy(
            mode = if (resetSavedMode) RestaurantBluetoothMode.STANDALONE else _status.value.mode,
            isRunning = false,
            isConnected = false,
            isConnecting = false,
            connectedPeers = emptyList(),
            statusMessage = "Bluetooth local arrêté"
        )
    }
}
