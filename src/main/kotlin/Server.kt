import javax.swing.*
import java.awt.FlowLayout
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class Server : JFrame("Server") {
    private val startButton = JButton("Start Server")
    private val stopButton = JButton("Stop Server")
    private val portField = JTextField("1027", 5)
    private var serverSocket: DatagramSocket? = null
    private var running = false
    private val clients = ConcurrentHashMap.newKeySet<Pair<InetAddress, Int>>()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = FlowLayout()
        add(JLabel("Port:"))
        add(portField)
        add(startButton)
        add(stopButton)
        pack()
        setLocationRelativeTo(null)
        isVisible = true

        startButton.addActionListener {
            if (!running) {
                try {
                    val port = portField.text.toInt()
                    serverSocket = DatagramSocket(port)
                    running = true
                    println("Server started on port $port")
                    Thread { listen() }.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        stopButton.addActionListener {
            if (running) {
                running = false
                serverSocket?.close()
                println("Server stopped")
            }
        }
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
                    e.printStackTrace()
                }
            }
        }
    }
}

fun main() {
    Server()
}