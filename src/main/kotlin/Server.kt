import javax.swing.*
import java.awt.FlowLayout
import java.awt.Color
import java.awt.Dimension
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class Server : JFrame("Server") {
    private val startButton = JButton("Start Server")
    private val stopButton = JButton("Stop Server")
    private val portField = JTextField("1027", 5)
    private val statusIndicator = JPanel()
    private var serverSocket: DatagramSocket? = null
    private var running = false
    private val clients = ConcurrentHashMap.newKeySet<Pair<InetAddress, Int>>()

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
                    println("Server started on port $port")
                    Thread { listen() }.start()
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
                println("Server stopped")
            }
        }
    }

    private fun updateStatus(status: ServerStatus) {
        statusIndicator.background = status.color
    }

    private fun listen() {
        val buffer = ByteArray(1024)
        while (running) {
            try {
                val packet = java.net.DatagramPacket(buffer, buffer.size)
                serverSocket?.receive(packet) ?: continue

                val clientAddress = packet.address
                val clientPort = packet.port
                val clientKey = Pair(clientAddress, clientPort)
                clients.add(clientKey)

                val data = packet.data
                val length = packet.length

                clients.forEach { client ->
                    if (client.first != clientAddress || client.second != clientPort) {
                        val sendPacket = java.net.DatagramPacket(data, length, client.first, client.second)
                        serverSocket?.send(sendPacket)
                    }
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