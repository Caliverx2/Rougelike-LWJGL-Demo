package org.lewapnoob.FileZero

import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.cos
import kotlin.math.sin

val grid1 = arrayOf(
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
)
val grid2 = arrayOf(
    intArrayOf(1, 1, 0, 1, 1, 1, 0, 1, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 1, 0, 1, 1, 1, 0, 1, 1)
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
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
)

var gridMap: Array<Array<Array<Int>>> = Array(9) {
    Array(9) {
        Array(9) { 0 }
    }
}

class DrawingPanel : JPanel() {
    init {
        addKeyListener(KeyboardListener())
        isFocusable = true

        for (x in 0..8) {
            for (y in 0..8) {
                if (grid1[x][y] == 1) {
                    gridMap[x][0][y] = 1
                }
                if (grid2[x][y] == 1) {
                    gridMap[x][1][y] = 1
                }
                if (grid3[x][y] == 1) {
                    gridMap[x][2][y] = 1
                }
                if (grid4[x][y] == 1) {
                    gridMap[x][3][y] = 1
                }
            }
        }

        val timer = Timer(16) { repaint() }
        timer.start()
    }


    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = Color.BLACK
        g2d.fillRect(0, 0, width, height)

        g2d.color = Color.WHITE
        for (x in 0..8) {
            for (y in 0..8) {
                if ((gridMap[x][0][y]) == 1) {
                    g2d.drawOval(x*10, y*10, 10, 10)
                }
                if ((gridMap[x][1][y]) == 1) {
                    g2d.drawOval(x*10, y*10+90*1+10, 10, 10)
                }
                if ((gridMap[x][2][y]) == 1) {
                    g2d.drawOval(x*10, y*10+90*2+20, 10, 10)
                }
                if ((gridMap[x][3][y]) == 1) {
                    g2d.drawOval(x*10, y*10+90*3+30, 10, 10)
                }
            }
        }
    }

    private inner class KeyboardListener : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
            when (e?.keyCode) {
                KeyEvent.VK_UP -> println("inglisz")
            }
        }

        override fun keyReleased(e: KeyEvent?) {
            if (e?.keyCode == KeyEvent.VK_G) {
                println("angleX")
            }
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("FileZero")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        val panel = DrawingPanel()
        panel.preferredSize = java.awt.Dimension(640, 400)
        frame.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}