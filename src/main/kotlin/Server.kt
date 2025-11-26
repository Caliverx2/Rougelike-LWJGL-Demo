package org.lewapnoob.FileZeroServer

import javax.swing.*
import java.awt.FlowLayout
import java.awt.Color
import java.awt.Dimension
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class Server : JFrame("Server") {
    private val startButton = JButton("Start Server")
    private val stopButton = JButton("Stop Server")
    private val portField = JTextField("1027", 5)
    private val statusIndicator = JPanel()

    private var serverSocket: DatagramSocket? = null
    private var running = false

    // Przechowuje informacje o klientach, kluczem jest ich adres sieciowy (IP, Port)
    private data class ClientInfo(
        var lastSeen: Long = System.currentTimeMillis(),
        var lastPositionData: String = "" // Ostatnia wiadomość z pozycją gracza (np. "POSITION,ip,x,y,z,yaw")
    )
    private val clients = ConcurrentHashMap<Pair<InetAddress, Int>, ClientInfo>()

    private enum class ServerStatus(val color: Color) {
        STOPPED(Color.BLACK),
        RUNNING(Color.GREEN),
        ERROR(Color.RED)
    }

    init {
        statusIndicator.preferredSize = Dimension(15, 15)
        updateStatus(ServerStatus.STOPPED)

        defaultCloseOperation = EXIT_ON_CLOSE
        layout = FlowLayout()
        add(JLabel("Port:"))
        add(portField)
        add(startButton)
        add(stopButton)
        add(JLabel("Status:"))
        add(statusIndicator)

        setSize(400, 75)
        setLocationRelativeTo(null)
        isVisible = true

        startButton.addActionListener {
            if (!running) {
                try {
                    val port = portField.text.toInt()
                    serverSocket = DatagramSocket(port)
                    running = true
                    updateStatus(ServerStatus.RUNNING)
                    println("Serwer uruchomiony na porcie $port")

                    thread(isDaemon = true) { listen() }
                    thread(isDaemon = true) { checkClientTimeouts() }

                } catch (e: Exception) {
                    updateStatus(ServerStatus.ERROR)
                    e.printStackTrace()
                }
            }
        }

        stopButton.addActionListener {
            if (running) {
                running = false
                serverSocket?.close()
                updateStatus(ServerStatus.STOPPED)
                clients.clear()
                println("Serwer zatrzymany")
            }
        }
    }

    private fun updateStatus(status: ServerStatus) {
        statusIndicator.background = status.color
    }

    private fun checkClientTimeouts() {
        while (running) {
            try {
                val now = System.currentTimeMillis()
                val clientsToRemove = mutableListOf<Pair<InetAddress, Int>>()

                // Znajdź klientów, którzy przekroczyli limit czasu
                clients.forEach { (clientAddress, clientInfo) ->
                    if (now - clientInfo.lastSeen > 2000) {
                        clientsToRemove.add(clientAddress)
                    }
                }

                // Usuń nieaktywnych klientów i poinformuj pozostałych
                clientsToRemove.forEach { clientAddress ->
                    val removedClientInfo = clients.remove(clientAddress) ?: return@forEach
                    println("Klient ${clientAddress.first.hostAddress} rozłączony (timeout).")
                    val disconnectMessage = "DISCONNECT,${clientAddress.first.hostAddress}".toByteArray()
                    // Wyślij ją do wszystkich pozostałych aktywnych klientów
                    clients.forEach { (addr, _) ->
                        val packet = java.net.DatagramPacket(disconnectMessage, disconnectMessage.size, addr.first, addr.second)
                        serverSocket?.send(packet)
                    }
                }

                Thread.sleep(1000)
            } catch (e: Exception) {
                if (running) e.printStackTrace()
            }
        }
    }

    private fun listen() {
        val buffer = ByteArray(1024)
        while (running) {
            try {
                val packet = java.net.DatagramPacket(buffer, buffer.size)
                serverSocket?.receive(packet) ?: continue

                val message = String(packet.data, 0, packet.length)
                val parts = message.split(',')
                if (parts.isEmpty()) continue

                val messageType = parts[0]
                val clientAddress = Pair(packet.address, packet.port)

                // Sprawdź typ wiadomości
                if (messageType == "POSITION" && parts.size == 5) {
                    // Wiadomość z pozycją od klienta: "POSITION,x,y,z,yaw"
                    val isNewClient = !clients.containsKey(clientAddress)

                    val clientInfo = clients.computeIfAbsent(clientAddress) {
                        println("Nowy klient połączony: ${packet.address.hostAddress}")
                        ClientInfo()
                    }

                    // Przygotuj wiadomość do rozesłania: "POSITION,ip,x,y,z,yaw"
                    val broadcastMessage = "POSITION,${clientAddress.first.hostAddress},${parts.slice(1..4).joinToString(",")}"

                    // Zawsze aktualizuj czas ostatniej aktywności i pozycję
                    clientInfo.lastSeen = System.currentTimeMillis()
                    clientInfo.lastPositionData = broadcastMessage

                    // Jeśli to nowy klient, wyślij mu pozycje wszystkich innych graczy
                    if (isNewClient) {
                        clients.forEach { (otherClientAddress, otherClientInfo) ->
                            if (otherClientAddress != clientAddress && otherClientInfo.lastPositionData.isNotEmpty()) {
                                val positionData = otherClientInfo.lastPositionData.toByteArray()
                                val sendPacket = java.net.DatagramPacket(positionData, positionData.size, clientAddress.first, clientAddress.second)
                                serverSocket?.send(sendPacket)
                            }
                        }
                    }

                    // Natychmiast roześlij otrzymaną pozycję do wszystkich INNYCH klientów
                    val data = broadcastMessage.toByteArray()
                    clients.forEach { (otherClientAddress, _) ->
                        if (otherClientAddress != clientAddress) {
                            val sendPacket = java.net.DatagramPacket(data, data.size, otherClientAddress.first, otherClientAddress.second)
                            serverSocket?.send(sendPacket)
                        }
                    }
                } else if (messageType == "P") {
                    // To jest wiadomość "keep-alive"
                    val clientInfo = clients[clientAddress]
                    // Jeśli klient istnieje, po prostu zaktualizuj jego czas. Jeśli nie, zignoruj.
                    // Klient musi najpierw wysłać pozycję, aby zostać zarejestrowanym.
                    clientInfo?.lastSeen = System.currentTimeMillis()
                }

            } catch (e: Exception) {
                if (running) {
                    updateStatus(ServerStatus.ERROR)
                    e.printStackTrace()
                }
            }
        }
    }
}

fun main() {
    Server()
}