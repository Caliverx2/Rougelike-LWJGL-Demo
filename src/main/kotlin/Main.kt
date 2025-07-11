package org.lewapnoob.opengl

import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.createCapabilities
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil.NULL
import java.awt.image.BufferedImage
import java.io.IOException
import java.lang.Math.toRadians
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin
import org.lwjgl.BufferUtils
import kotlin.intArrayOf

fun main() {
    Main().run()
}

enum class BlockType(val id: Int, val texturePath: String?) {
    EMPTY(0, null), // Pusty blok, bez tekstury
    BLACK_BRICKS(1, "textures/black_bricks.png"),
    RED_BRICKS(2, "textures/bricks.jpg"),
    FLOOR(3, "textures/floor.jpg"),
    CEILING(4, "textures/ceiling.jpg");

    companion object {
        private val byId = values().associateBy { it.id }
        fun fromId(id: Int) = byId[id] ?: EMPTY
    }
}

class Main {
    private var window: Long = NULL
    private val textureIdMap = mutableMapOf<Int, Int>()

    private val width = 320*3
    private val height = 180*3
    private val title = "LWJGL Demo"

    private var cameraX = 4.0f
    private var cameraY = 1.85f
    private var cameraZ = 4.0f

    private var yaw = 180.0f
    private var pitch = 0.0f

    private var lastX: Double = width / 2.0
    private var lastY: Double = height / 2.0
    private var firstMouse = true
    private val mouseSensitivity = 0.1f
    private val pressedKeys = mutableSetOf<Int>()

    private val fogColor = floatArrayOf(0.3f, 0.3f, 0.3f, 0.5f)
    private val fogDensity = 0.14f
    private val fogMode = GL_EXP2


    val grid1 = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 3, 3, 3, 3, 3, 3, 3, 1),
        intArrayOf(1, 3, 3, 3, 3, 3, 3, 3, 1),
        intArrayOf(1, 3, 3, 3, 3, 3, 3, 3, 1),
        intArrayOf(1, 3, 3, 3, 3, 3, 3, 3, 1),
        intArrayOf(1, 3, 3, 3, 3, 3, 3, 3, 1),
        intArrayOf(1, 3, 3, 3, 3, 3, 3, 3, 1),
        intArrayOf(1, 3, 3, 3, 3, 3, 3, 3, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
    )
    val grid2 = arrayOf(
        intArrayOf(1, 1, 0, 1, 1, 1, 0, 1, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(2, 0, 0, 0, 2, 0, 0, 0, 2),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 1, 0, 1, 2, 1, 0, 1, 1)
    )
    val grid3 = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
    )
    val grid4 = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 4, 4, 4, 4, 4, 4, 4, 1),
        intArrayOf(1, 4, 4, 4, 4, 4, 4, 4, 1),
        intArrayOf(1, 4, 4, 4, 4, 4, 4, 4, 1),
        intArrayOf(1, 4, 4, 4, 4, 4, 4, 4, 1),
        intArrayOf(1, 4, 4, 4, 4, 4, 4, 4, 1),
        intArrayOf(1, 4, 4, 4, 4, 4, 4, 4, 1),
        intArrayOf(1, 4, 4, 4, 4, 4, 4, 4, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
    )

    private lateinit var gridMap: GridMap

    fun run() {
        init()
        loop()

        glDeleteTextures(textureIdMap.values.toIntArray())
        glfwFreeCallbacks(window)
        glfwDestroyWindow(window)

        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }

    private fun init() {
        println()
        gridMap = GridMap(20, 5, 20)
        /*
        for (x in 0..8) {
            for (y in 0..8) {
                if (grid1[x][y] == 1) {
                    gridMap.setBlock(x, 0, y, 1)
                } else if (grid1[x][y] == 3) {
                    gridMap.setBlock(x, 0, y, 3)
                }
                if (grid2[x][y] == 1) {
                    gridMap.setBlock(x+3, 1, y+3, 1)
                } else if (grid2[x][y] == 2) {
                    gridMap.setBlock(x+3, 1, y+3, 2)
                }
                if (grid3[x][y] == 1) {
                    gridMap.setBlock(x+3, 2, y+3, 1)
                }
                if (grid4[x][y] == 1) {
                    gridMap.setBlock(x+3, 3, y+3, 1)
                } else if (grid4[x][y] == 4) {
                    gridMap.setBlock(x+3, 3, y+3, 4)
                }
            }
        }*/
        //gridMap.setBlock(2, 1, 2, true)

        val layers = listOf(grid1, grid2, grid3, grid4)
        layers.forEachIndexed { y, grid ->
            grid.forEachIndexed { x, row ->
                row.forEachIndexed { z, blockId ->
                    if (blockId != 0) {
                        gridMap.setBlock(x+1, y, z+1, blockId)
                    }
                }
            }
        }

        glfwSetErrorCallback { error, description ->
            System.err.println("GLFW Error $error: ${org.lwjgl.system.MemoryUtil.memUTF8(description)}")
        }

        if (!glfwInit()) {
            throw IllegalStateException("Nie można zainicjalizować GLFW")
        }

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE)

        window = glfwCreateWindow(width, height, title, NULL, NULL)
        if (window == NULL) {
            throw RuntimeException("Nie można stworzyć okna GLFW")
        }

        glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true)
            }
            if (action == GLFW_PRESS) {
                pressedKeys.add(key)
            } else if (action == GLFW_RELEASE) {
                pressedKeys.remove(key)
            }

            if (key == GLFW_KEY_G && action == GLFW_RELEASE) {
                println("X: ${cameraX.toInt()}, Y: ${cameraY.toInt()}, Z: ${cameraZ.toInt()}")
                println(gridMap.getBlock(cameraX.toInt(), cameraY.toInt(), cameraZ.toInt()))
            }
        }


        glfwSetCursorPosCallback(window) { window, xpos, ypos ->
            if (firstMouse) {
                lastX = xpos
                lastY = ypos
                firstMouse = false
            }

            val xoffset = (xpos - lastX).toFloat()
            val yoffset = (lastY - ypos).toFloat()

            lastX = xpos
            lastY = ypos

            yaw += xoffset * mouseSensitivity
            pitch -= yoffset * mouseSensitivity

            if (pitch > 89.0f) pitch = 89.0f
            if (pitch < -89.0f) pitch = -89.0f
        }

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

        glfwMakeContextCurrent(window)
        glfwSwapInterval(1)
        glfwShowWindow(window)

        createCapabilities()

        println("Ładowanie tekstur...")
        BlockType.values().forEach { blockType ->
            blockType.texturePath?.let { path ->
                val textureId = loadTexture(path)
                if (textureId != 0) {
                    textureIdMap[blockType.id] = textureId
                    println("Załadowano teksturę dla ${blockType.name} (ID: $textureId)")
                }
            }
        }
        glEnable(GL_TEXTURE_2D)

        glClearColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]) // Kolor tła
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_FOG)
        val fogColorBuffer = BufferUtils.createFloatBuffer(4).put(fogColor).flip()
        glFogfv(GL_FOG_COLOR, fogColorBuffer)
        glFogi(GL_FOG_MODE, fogMode)
        glFogf(GL_FOG_DENSITY, fogDensity)
        glHint(GL_FOG_HINT, GL_NICEST)
    }

    private fun loadTexture(filePath: String): Int {
        try {
            val bufferedImage: BufferedImage = ImageIO.read(this::class.java.classLoader.getResource(filePath))
                ?: throw IOException("Nie znaleziono pliku tekstury: $filePath")

            val width = bufferedImage.width
            val height = bufferedImage.height

            val pixels = IntArray(width * height)
            bufferedImage.getRGB(0, 0, width, height, pixels, 0, width)

            val buffer = BufferUtils.createByteBuffer(width * height * 4)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    buffer.put((pixel shr 16 and 0xFF).toByte())
                    buffer.put((pixel shr 8 and 0xFF).toByte())
                    buffer.put((pixel and 0xFF).toByte())
                    buffer.put((pixel shr 24 and 0xFF).toByte())
                }
            }
            buffer.flip()

            val textureID = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, textureID)

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
            glBindTexture(GL_TEXTURE_2D, 0)

            return textureID
        } catch (e: IOException) {
            System.err.println("Błąd ładowania tekstury '$filePath': ${e.message}")
            glfwSetWindowShouldClose(window, true)
            return 0
        }
    }

    private fun checkCollision(newX: Float, newY: Float, newZ: Float): Boolean {
        val playerHalfWidth = 0.4f
        val playerHeight = 0.2f

        val minX = (newX - playerHalfWidth).toInt()
        val maxX = (newX + playerHalfWidth).toInt()
        val minY = newY.toInt()
        val maxY = (newY + playerHeight - 0.1f).toInt()
        val minZ = (newZ - playerHalfWidth).toInt()
        val maxZ = (newZ + playerHalfWidth).toInt()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val blockType = gridMap.getBlock(x, y, z)
                    if (blockType != BlockType.EMPTY.id && blockType != BlockType.RED_BRICKS.id) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

            glMatrixMode(GL_PROJECTION)
            glLoadIdentity()
            val aspectRatio = width.toFloat() / height.toFloat()
            val fov = 90.0f
            val zNear = 0.1f
            val zFar = 30.0f
            val top = zNear * kotlin.math.tan(toRadians(fov / 2.0)).toFloat()
            val bottom = -top
            val right = top * aspectRatio
            val left = -right
            glFrustum(left.toDouble(), right.toDouble(), bottom.toDouble(), top.toDouble(), zNear.toDouble(), zFar.toDouble())

            glMatrixMode(GL_MODELVIEW)
            glLoadIdentity()
            glRotatef(pitch, 1.0f, 0.0f, 0.0f)
            glRotatef(yaw, 0.0f, 1.0f, 0.0f)
            glTranslatef(-cameraX, -cameraY, -cameraZ)
            gridMap.render(textureIdMap)
            glfwSwapBuffers(window)
            glfwPollEvents()

            val cameraFrontX = (cos(toRadians(yaw.toDouble())) * cos(toRadians(pitch.toDouble()))).toFloat()
            val cameraFrontY = sin(toRadians(pitch.toDouble())).toFloat()
            val cameraFrontZ = (sin(toRadians(yaw.toDouble())) * cos(toRadians(pitch.toDouble()))).toFloat()

            var frontVecX = cameraFrontX
            var frontVecZ = cameraFrontZ
            val lengthFront = kotlin.math.sqrt((frontVecX * frontVecX + frontVecZ * frontVecZ).toDouble()).toFloat()
            if (lengthFront != 0.0f) {
                frontVecX /= lengthFront
                frontVecZ /= lengthFront
            }

            var rightVecX = (cos(toRadians(yaw.toDouble() + 90.0))).toFloat()
            var rightVecZ = (sin(toRadians(yaw.toDouble() + 90.0))).toFloat()
            val lengthRight = kotlin.math.sqrt((rightVecX * rightVecX + rightVecZ * rightVecZ).toDouble()).toFloat()
            if (lengthRight != 0.0f) {
                rightVecX /= lengthRight
                rightVecZ /= lengthRight
            }

            var moveSpeed = 0.07f
            var newCameraX = cameraX
            var newCameraY = cameraY
            var newCameraZ = cameraZ

            if (pressedKeys.contains(GLFW_KEY_LEFT_SHIFT)) {
                moveSpeed = 0.11f
            } else moveSpeed = 0.07f
            if (pressedKeys.contains(GLFW_KEY_W)) {
                newCameraX -= rightVecX * moveSpeed
                newCameraZ -= rightVecZ * moveSpeed
            }
            if (pressedKeys.contains(GLFW_KEY_S)) {
                newCameraX += rightVecX * moveSpeed
                newCameraZ += rightVecZ * moveSpeed
            }
            if (pressedKeys.contains(GLFW_KEY_A)) {
                newCameraX -= frontVecX * moveSpeed
                newCameraZ -= frontVecZ * moveSpeed
            }
            if (pressedKeys.contains(GLFW_KEY_D)) {
                newCameraX += frontVecX * moveSpeed
                newCameraZ += frontVecZ * moveSpeed
            }

            if (!checkCollision(newCameraX, cameraY, cameraZ)) {
                cameraX = newCameraX
            }
            if (!checkCollision(cameraX, newCameraY, cameraZ)) {
                cameraY = newCameraY
            }
            if (!checkCollision(cameraX, cameraY, newCameraZ)) {
                cameraZ = newCameraZ
            }

            if (pressedKeys.contains(GLFW_KEY_SPACE)) {
                cameraY += moveSpeed
            }
            if (pressedKeys.contains(GLFW_KEY_LEFT_CONTROL)) {
                cameraY -= moveSpeed
            }

            val playerHeight = 1.75f
            val playerWidth = 0.75f
            val minBlockX = (cameraX - playerWidth / 2).toInt()
            val maxBlockX = (cameraX + playerWidth / 2).toInt()
            val minBlockY = cameraY.toInt()
            val maxBlockY = (cameraY + playerHeight).toInt()
            val minBlockZ = (cameraZ - playerWidth / 2).toInt()
            val maxBlockZ = (cameraZ + playerWidth / 2).toInt()

            for (x in minBlockX..maxBlockX) {
                for (y in minBlockY..maxBlockY) {
                    for (z in minBlockZ..maxBlockZ) {
                        if (gridMap.getBlock(x, y, z) == BlockType.RED_BRICKS.id) {
                            println("Kolizja z blokiem ${BlockType.RED_BRICKS.name}! X: $x, Y: $y, Z: $z")
                            gridMap.setBlock(x, y, z, BlockType.EMPTY.id)
                        }
                    }
                }
            }
        }
    }
}

object Cube {
    private val vertices = floatArrayOf(
        // Przednia ściana
        -0.5f, -0.5f,  0.5f,
        0.5f, -0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,
        -0.5f,  0.5f,  0.5f,

        // Tylna ściana
        -0.5f, -0.5f, -0.5f,
        -0.5f,  0.5f, -0.5f,
        0.5f,  0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,

        // Górna ściana
        -0.5f,  0.5f, -0.5f,
        -0.5f,  0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,
        0.5f,  0.5f, -0.5f,

        // Dolna ściana
        -0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f, -0.5f,  0.5f,
        -0.5f, -0.5f,  0.5f,

        // Prawa ściana
        0.5f, -0.5f, -0.5f,
        0.5f,  0.5f, -0.5f,
        0.5f,  0.5f,  0.5f,
        0.5f, -0.5f,  0.5f,

        // Lewa ściana
        -0.5f, -0.5f, -0.5f,
        -0.5f, -0.5f,  0.5f,
        -0.5f,  0.5f,  0.5f,
        -0.5f,  0.5f, -0.5f
    )

    private val indices = intArrayOf(
        0, 1, 2, 3,         // Przednia
        4, 5, 6, 7,         // Tylna
        8, 9, 10, 11,       // Górna
        12, 13, 14, 15,     // Dolna
        16, 17, 18, 19,     // Prawa
        20, 21, 22, 23      // Lewa
    )

    private val texCoords = floatArrayOf(
        // Przednia ściana
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f,
        0.0f, 1.0f,

        // Tylna ściana
        1.0f, 0.0f,
        1.0f, 1.0f,
        0.0f, 1.0f,
        0.0f, 0.0f,

        // Górna ściana
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f,

        // Dolna ściana
        1.0f, 1.0f,
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,

        // Prawa ściana
        1.0f, 0.0f,
        1.0f, 1.0f,
        0.0f, 1.0f,
        0.0f, 0.0f,

        // Lewa ściana
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f,
        0.0f, 1.0f
    )

    fun render() {
        glBegin(GL_QUADS)
        for (i in indices.indices) {
            val vertexIndex = indices[i]
            val texCoordIndex = vertexIndex * 2

            glTexCoord2f(texCoords[texCoordIndex], texCoords[texCoordIndex + 1])
            glVertex3f(vertices[vertexIndex * 3], vertices[vertexIndex * 3 + 1], vertices[vertexIndex * 3 + 2])
        }
        glEnd()
    }
}

class GridMap(val width: Int, val height: Int, val depth: Int) {
    private val blocks: Array<Array<IntArray>> = Array(width) { Array(height) { IntArray(depth) { 0 } } }

    /**
     * Ustawia typ bloku w danej pozycji (x, y, z).
     * @param type typ bloku (0 dla pustego, inne wartości dla różnych wariantów).
     */
    fun setBlock(x: Int, y: Int, z: Int, type: Int) {
        if (x in 0 until width && y in 0 until height && z in 0 until depth) {
            blocks[x][y][z] = type
        } else {
            System.err.println("Pozycja ($x, $y, $z) poza zakresem mapy.")
        }
    }

    /**
     * Zwraca typ bloku w danej pozycji.
     */
    fun getBlock(x: Int, y: Int, z: Int): Int {
        return if (x in 0 until width && y in 0 until height && z in 0 until depth) {
            blocks[x][y][z]
        } else {
            0
        }
    }

    /**
     * Renderuje wszystkie bloki na mapie.
     * Teraz przyjmuje mapę ID tekstur, gdzie kluczem jest typ bloku.
     */
    fun render(textureIDs: Map<Int, Int>) {
        glEnable(GL_TEXTURE_2D)

        for (x in 0 until width) {
            for (y in 0 until height) {
                for (z in 0 until depth) {
                    val blockType = getBlock(x, y, z)
                    if (blockType != 0) {
                        val textureID = textureIDs[blockType]
                        if (textureID != null) {
                            glBindTexture(GL_TEXTURE_2D, textureID)
                            glPushMatrix()
                            glTranslatef(x.toFloat() + 0.5f, y.toFloat() + 0.5f, z.toFloat() + 0.5f)
                            Cube.render()
                            glPopMatrix()
                        } else {
                            System.err.println("Brak tekstury dla typu bloku: $blockType")
                        }
                    }
                }
            }
        }
        glBindTexture(GL_TEXTURE_2D, 0)
        glDisable(GL_TEXTURE_2D)
    }
}