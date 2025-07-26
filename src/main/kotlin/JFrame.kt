package org.lewapnoob.JFrame

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

class DemoDisplay : JPanel() {
    val fov = Math.toRadians(90.0)
    val num_rays = 90
    val max_depth = 15.0
    val p1 = player()

    init {
        addKeyListener(KeyboardListener())
        preferredSize = Dimension(1920 / 2, 1080 / 2)
        setBackground(Color.DARK_GRAY)
        isFocusable = true
        loop()
    }

    private inner class KeyboardListener : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
            if (e?.keyCode == KeyEvent.VK_D) {
                p1.dir = p1.dir + Math.toRadians(5.0)
            }
            if (e?.keyCode == KeyEvent.VK_A) {
                p1.dir = p1.dir - Math.toRadians(5.0)
            }
            if (e?.keyCode == KeyEvent.VK_W) {
                p1.positionX = p1.positionX + cos(p1.dir) * 0.1
                p1.positionY = p1.positionY + sin(p1.dir) * 0.1
            }
            if (e?.keyCode == KeyEvent.VK_S) {
                p1.positionX = p1.positionX - cos(p1.dir) * 0.1
                p1.positionY = p1.positionY - sin(p1.dir) * 0.1
            }
        }
    }

    fun castRay(angle : Double): Double {
        val dx = cos(angle)
        val dy = sin(angle)

        var dist = 0.1
        val step = 0.01

        while (dist < max_depth) {
            val postionx = p1.positionX + dx * dist
            val postiony = p1.positionY + dy * dist
            val cx = floor(postionx).toInt()
            val cy = floor(postiony).toInt()

            if (cx < 0 || cx >= grid.size || cy < 0 || cy >= grid.size) break

            if (grid[cy][cx] == 1) {
                return dist
            }

            dist += step
        }

        return max_depth
    }

    fun loop() {
        Timer(16) {
            repaint()
        }.start()
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        val g2d = g as Graphics2D

        clear(g2d)

        val sw = width
        val sh = height
        val columnWidth = floor((sw / num_rays).toDouble())
        val last_x = 0
        for (i in 0 until num_rays) {
            val x_start_f = (i.toDouble() / num_rays.toDouble()) * sw
            val x_end_f = ((i.toDouble() + 1.0) / num_rays.toDouble()) * sw

            val x_start = x_start_f.toInt()
            val x_end = x_end_f.toInt()
            val columnScreenX = x_start
            var columnWidth = x_end - x_start

            if (columnWidth <= 0) {
                columnWidth = 1
            }

            val angle = p1.dir - (fov / 2) + (i.toDouble() / (num_rays - 1).toDouble()) * fov
            val dist = castRay(angle)
            val brightness = max(0.0,(255 - dist * 40)).toInt()
            val shade = Color(brightness, brightness ,brightness)

            val correctedDist = dist * cos(angle - p1.dir)
            val colHeight = floor(sh / (correctedDist + 0.1))
            val y = (sh - colHeight) / 2

            g2d.color = shade
            g2d.fillRect(columnScreenX, y.toInt(), columnWidth, colHeight.toInt())
        }

        g2d.color = Color.LIGHT_GRAY
        g2d.font = Font("Arial", Font.BOLD, 20)
        g2d.drawString("${LocalTime.now().hour}:${LocalTime.now().minute}", 1920/2 - 100, 30)

        val sizeOval = 17
        for (x in 0..grid.size-1) {
            for (y in 0..grid.size-1) {
                g2d.color = Color(100, 100, 100)
                if (grid[x][y] == 1) g2d.fillOval(sizeOval*y, sizeOval*x,sizeOval, sizeOval)
            }
        }

        g2d.color = Color(188, 40, 40)
        g2d.fillOval((sizeOval*p1.positionX).toInt(), (sizeOval*p1.positionY).toInt(),sizeOval*3/4, sizeOval*3/4)
    }

    private fun clear(g: Graphics2D) {
        g.color = Color.DARK_GRAY
        g.fillRect(0, 0, 1920/2, 1080/2)
    }
}

class player() {
    var positionX = 2.5
    var positionY = 2.5
    var dir = 0.0
}

val grid = arrayOf(
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1),
    intArrayOf(1,0,0,0,0,0,0,0,0,0,0,1),
    intArrayOf(1,0,0,0,0,1,1,0,0,0,0,1),
    intArrayOf(1,0,0,1,0,0,0,0,1,0,0,1),
    intArrayOf(1,0,1,0,0,0,0,0,0,1,0,1),
    intArrayOf(1,0,1,0,0,0,0,0,0,1,0,1),
    intArrayOf(1,0,0,0,0,1,1,0,0,0,0,1),
    intArrayOf(1,0,0,0,1,0,0,1,0,0,0,1),
    intArrayOf(1,0,0,1,0,0,0,0,1,0,0,1),
    intArrayOf(1,0,1,0,0,0,0,0,0,1,0,1),
    intArrayOf(1,0,0,0,0,0,0,0,0,0,0,1),
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1),
)

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("JFrame.kt")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.add(DemoDisplay())
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}